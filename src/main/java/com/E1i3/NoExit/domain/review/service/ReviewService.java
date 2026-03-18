package com.E1i3.NoExit.domain.review.service;

import com.E1i3.NoExit.domain.common.domain.DelYN;
import com.E1i3.NoExit.domain.member.domain.Member;
import com.E1i3.NoExit.domain.member.repository.MemberRepository;
import com.E1i3.NoExit.domain.reservation.domain.Reservation;
import com.E1i3.NoExit.domain.reservation.domain.ReservationStatus;
import com.E1i3.NoExit.domain.reservation.repository.ReservationRepository;
import com.E1i3.NoExit.domain.review.domain.Review;
import com.E1i3.NoExit.domain.review.dto.ReviewListDto;
import com.E1i3.NoExit.domain.review.dto.ReviewSaveDto;
import com.E1i3.NoExit.domain.review.dto.ReviewUpdateDto;
import com.E1i3.NoExit.domain.review.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReviewService {
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.folder.folderName2}")
    private String reviewFolder;

    private final S3Client s3Client;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    public ReviewService(S3Client s3Client, ReviewRepository reviewRepository, MemberRepository memberRepository, ReservationRepository reservationRepository) {
        this.s3Client = s3Client;
        this.memberRepository = memberRepository;
        this.reviewRepository = reviewRepository;
        this.reservationRepository = reservationRepository;
    }

    /*  */
   @PreAuthorize("hasRole('USER')")
    @Transactional
    public Review createReview(ReviewSaveDto dto) {
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보를 찾을 수 없습니다."));
    
        Reservation reservation = reservationRepository.findById(dto.getReservationId())
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않은 예약입니다."));
    
        if (reservation.getReservationStatus() != ReservationStatus.ACCEPT) {
            throw new IllegalStateException("리뷰를 작성할 수 없는 상태입니다. 예약이 승인되지 않았습니다.");
        }
    
        Review existingReview = reviewRepository.findByReservationAndDelYN(reservation, DelYN.Y).orElse(null);
        if (existingReview != null) {
            existingReview.cancelAndRecreateYN();
            existingReview.updateContent(dto.getContent(), dto.getRating(), dto.getReviewImage());
            if (dto.getReviewImage() != null && !dto.getReviewImage().isEmpty()) {
                updateReviewImage(existingReview, dto.getReviewImage());
            }
            return reviewRepository.save(existingReview);
        }
    
        Review review = dto.toEntity(member, reservation);
        Review savedReview = reviewRepository.save(review);
    
        MultipartFile image = dto.getReviewImage();
        if (image != null && !image.isEmpty()) {
            updateReviewImage(savedReview, image);
        }
    
        return reviewRepository.save(savedReview);
    }

    private void updateReviewImage(Review review, MultipartFile image) {
        try {
            String fileName = review.getId() + "_" + image.getOriginalFilename();
            String s3Key = reviewFolder + fileName;

            // S3에 파일 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(image.getBytes()));

            String s3Path = s3Client.utilities().getUrl(a -> a.bucket(bucket).key(s3Key)).toExternalForm();
            review.updateImagePath(s3Path);
        } catch (IOException e) {
            throw new RuntimeException("이미지 업로드 실패", e);
        }
    }


    /* 게임 아이디를 통한 리뷰 리스트(전체사용자 기준 gameId 별) */
    public Page<ReviewListDto> getReviewsByGameId(Long gameId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByReservation_GameIdAndDelYN(gameId, DelYN.N, pageable);
        // 로그를 통해 반환된 리뷰의 내용을 확인합니다.
        reviews.forEach(review -> System.out.println("Review ID: " + review.getId() + ", Game ID: " + review.getReservation().getGame().getId()));
        return reviews.map(Review::fromEntity);
    }

    /* 리뷰 리스트(전체 사용자 기준 전체 리스트) */
    public Page<ReviewListDto> pageReview(Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByDelYN(DelYN.N, pageable);
        return reviews.map(Review::fromEntity);
    }

    /* 리뷰리스트(내가 쓴 글 리스트 ) */
    @PreAuthorize("hasRole('USER')")
    public Page<ReviewListDto> getUserReviews(Pageable pageable) {
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보를 찾을 수 없습니다."));

        Page<Review> reviews = reviewRepository.findByMemberAndDelYN(member, DelYN.N, pageable);
        return reviews.map(Review::fromEntity);
    }

    /* 리뷰 삭제 */
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public Review cancelReview(Long reviewId) {
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보를 찾을 수 없습니다."));

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다."));

        if (!review.getMember().equals(member)) {
            throw new IllegalArgumentException("본인이 작성한 리뷰만 삭제할 수 있습니다.");
        }

        review.updateDelYN();
        reviewRepository.save(review);
        return review;
    }

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public Review updateReview(Long reviewId, ReviewUpdateDto dto) {
        String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new EntityNotFoundException("회원 정보를 찾을 수 없습니다."));

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("리뷰를 찾을 수 없습니다."));

        if (!review.getMember().equals(member)) {
            throw new IllegalArgumentException("본인이 작성한 리뷰만 수정할 수 있습니다.");
        }

        // 이미지 처리
        String s3Path = null;
        if (dto.getReviewImage() != null && !dto.getReviewImage().isEmpty()) {
            try {
                String fileName = review.getId() + "_" + dto.getReviewImage().getOriginalFilename();
                String s3Key = reviewFolder + fileName;

                // S3에 파일 업로드
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromBytes(dto.getReviewImage().getBytes()));

                s3Path = s3Client.utilities().getUrl(a -> a.bucket(bucket).key(s3Key)).toExternalForm();
                review.updateImagePath(s3Path);
            } catch (IOException e) {
                throw new RuntimeException("이미지 업로드 실패", e);
            }
        }

        // 내용과 평점 업데이트 (이미지 경로는 여기서 처리하지 않음)
        review.updateContentAndRating(dto.getContent(), dto.getRating());
        return reviewRepository.save(review);
    }





    /* 리뷰 숫자 */
    public long getReviewCountForGame(Long gameId) {
        return reviewRepository.countByReservation_GameIdAndDelYN(gameId, DelYN.N);
    }
    /* 리뷰 평균 값 */
    public double calculateAverageRatingForGame(Long gameId) {
        List<Review> reviews = reviewRepository.findByReservation_GameIdAndDelYN(gameId, DelYN.N);
        return reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
    }
}
