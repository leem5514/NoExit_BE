package com.E1i3.NoExit.domain.board.service;

import com.E1i3.NoExit.domain.board.domain.Board;
import com.E1i3.NoExit.domain.board.domain.BoardType;
import com.E1i3.NoExit.domain.board.dto.BoardCreateReqDto;
import com.E1i3.NoExit.domain.board.dto.BoardDetailResDto;
import com.E1i3.NoExit.domain.board.dto.BoardListResDto;
import com.E1i3.NoExit.domain.board.dto.BoardSearchDto;
import com.E1i3.NoExit.domain.board.dto.BoardUpdateReqDto;
import com.E1i3.NoExit.domain.board.repository.BoardListProjection;
import com.E1i3.NoExit.domain.board.repository.BoardRepository;
import com.E1i3.NoExit.domain.boardimage.domain.BoardImage;
import com.E1i3.NoExit.domain.boardimage.repository.BoardImageRepository;
import com.E1i3.NoExit.domain.common.domain.DelYN;
import com.E1i3.NoExit.domain.common.service.S3Service;
import com.E1i3.NoExit.domain.member.domain.Member;
import com.E1i3.NoExit.domain.member.repository.MemberRepository;
import com.E1i3.NoExit.domain.notification.controller.SseController;
import com.E1i3.NoExit.domain.notification.domain.NotificationType;
import com.E1i3.NoExit.domain.notification.dto.NotificationResDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.persistence.EntityNotFoundException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final MemberRepository memberRepository;
    private final S3Service s3Service;
    private final BoardImageRepository boardImageRepository;
    private final SseController sseController;
    private final RedisTemplate<String, Object> boardRedisTemplate;

    private static final String BOARD_PREFIX = "board:";
    private static final String MEMBER_PREFIX = "member:";

    public BoardService(
            BoardRepository boardRepository,
            MemberRepository memberRepository,
            S3Service s3Service,
            BoardImageRepository boardImageRepository,
            SseController sseController,
            @Qualifier("4") RedisTemplate<String, Object> boardRedisTemplate
    ) {
        this.boardRepository = boardRepository;
        this.memberRepository = memberRepository;
        this.s3Service = s3Service;
        this.boardImageRepository = boardImageRepository;
        this.sseController = sseController;
        this.boardRedisTemplate = boardRedisTemplate;
    }

    @Transactional
    public Board boardCreate(BoardCreateReqDto dto, List<MultipartFile> imgFiles) {
        Member member = getCurrentMember();

        Board board = Board.builder()
                .member(member)
                .title(dto.getTitle())
                .contents(dto.getContents())
                .boardType(dto.getBoardType())
                .build();

        Board savedBoard = boardRepository.save(board);
        appendImages(savedBoard, imgFiles);
        return savedBoard;
    }

    public Page<BoardListResDto> boardList(BoardSearchDto searchDto, Pageable pageable) {
        String title = normalize(searchDto != null ? searchDto.getSearchTitle() : null);
        String contents = normalize(searchDto != null ? searchDto.getSearchContents() : null);
        BoardType boardType = parseBoardType(searchDto != null ? searchDto.getSearchBoardType() : null);

        Page<BoardListProjection> rows = boardRepository.searchBoards(title, contents, boardType, pageable);

        return rows.map(row -> BoardListResDto.builder()
                .id(row.getId())
                .writer(row.getWriter())
                .title(row.getTitle())
                .boardHits(row.getBoardHits())
                .likes(row.getLikes())
                .comments((int) row.getCommentCount())
                .boardType(row.getBoardType())
                .img(row.getImageCount() > 0)
                .createdDate(row.getCreatedTime().toLocalDate())
                .build());
    }

    @Transactional
    public BoardDetailResDto boardDetail(Long id) {
        Board board = boardRepository.findDetailBoardById(id)
                .orElseThrow(() -> new EntityNotFoundException("Board not found with id: " + id));

        if (board.getDelYN() == DelYN.Y) {
            throw new IllegalArgumentException("cannot find board");
        }

        board.updateBoardHits();
        return board.detailFromEntity();
    }

    @Transactional
    public Board boardUpdate(Long id, BoardUpdateReqDto dto, List<MultipartFile> imgFiles) {
        String email = getCurrentMemberEmail();
        Board board = getBoardOrThrow(id);

        if (!board.getMember().getEmail().equals(email)) {
            throw new IllegalArgumentException("본인의 게시글만 수정할 수 있습니다.");
        }

        board.updateEntity(dto);
        appendImages(board, imgFiles);

        return boardRepository.save(board);
    }

    @Transactional
    public void boardDelete(Long id) {
        String email = getCurrentMemberEmail();
        Board board = getBoardOrThrow(id);

        if (!board.getMember().getEmail().equals(email)) {
            throw new IllegalArgumentException("본인의 게시글만 삭제할 수 있습니다.");
        }
        if (board.getDelYN() == DelYN.Y) {
            throw new IllegalArgumentException("cannot delete board");
        }

        board.deleteEntity();
        boardRepository.save(board);
    }

    @Transactional
    public boolean boardUpdateLikes(Long id) {
        boolean value = false;

        Member member = getCurrentMember();
        String email = member.getEmail();
        Board board = getBoardOrThrow(id);

        String likesKey = BOARD_PREFIX + id + ":likes";
        String memberLikesKey = MEMBER_PREFIX + member.getId() + ":likes:" + id;

        Boolean isLiked = boardRedisTemplate.hasKey(memberLikesKey);

        if (Boolean.TRUE.equals(isLiked)) {
            boardRedisTemplate.delete(memberLikesKey);
            boardRedisTemplate.opsForSet().remove(likesKey, member.getId());
            board.updateLikes(false);
        } else {
            boardRedisTemplate.opsForValue().set(memberLikesKey, true);
            boardRedisTemplate.opsForSet().add(likesKey, member.getId());
            board.updateLikes(true);
            value = true;

            String receiverEmail = board.getMember().getEmail();
            if (!receiverEmail.equals(email)) {
                NotificationResDto notificationResDto = NotificationResDto.builder()
                        .notification_id(board.getId())
                        .email(receiverEmail)
                        .sender_email(email)
                        .type(NotificationType.BOARD_LIKE)
                        .message(member.getNickname() + "님이 내 게시글을 추천합니다.")
                        .build();
                sseController.publishMessage(notificationResDto, receiverEmail);
            }
        }

        boardRepository.save(board);
        return value;
    }

    @Transactional
    public boolean boardUpdateDislikes(Long id) {
        boolean value = false;

        Member member = getCurrentMember();
        Board board = getBoardOrThrow(id);

        String dislikesKey = BOARD_PREFIX + id + ":dislikes";
        String memberDislikesKey = MEMBER_PREFIX + member.getId() + ":dislikes:" + id;

        Boolean isDisliked = boardRedisTemplate.hasKey(memberDislikesKey);

        if (Boolean.TRUE.equals(isDisliked)) {
            boardRedisTemplate.delete(memberDislikesKey);
            boardRedisTemplate.opsForSet().remove(dislikesKey, member.getId());
            board.updateDislikes(false);
        } else {
            boardRedisTemplate.opsForValue().set(memberDislikesKey, true);
            boardRedisTemplate.opsForSet().add(dislikesKey, member.getId());
            board.updateDislikes(true);
            value = true;
        }

        boardRepository.save(board);
        return value;
    }

    private void appendImages(Board board, List<MultipartFile> imgFiles) {
        if (imgFiles == null || imgFiles.isEmpty()) {
            return;
        }

        for (MultipartFile file : imgFiles) {
            BoardImage img = BoardImage.builder()
                    .board(board)
                    .imageUrl(s3Service.uploadFile(file, "board"))
                    .build();
            board.getImgs().add(img);
            boardImageRepository.save(img);
        }
    }

    private Member getCurrentMember() {
        String email = getCurrentMemberEmail();
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("없는 회원입니다."));
    }

    private String getCurrentMemberEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Board getBoardOrThrow(Long id) {
        return boardRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Board not found with id: " + id));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BoardType parseBoardType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BoardType.valueOf(value.trim());
    }
}
