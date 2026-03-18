package com.E1i3.NoExit.domain.member.service;

import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.E1i3.NoExit.domain.board.domain.Board;
import com.E1i3.NoExit.domain.board.dto.BoardListResDto;
import com.E1i3.NoExit.domain.common.domain.DelYN;
import com.E1i3.NoExit.domain.common.dto.LoginReqDto;
import com.E1i3.NoExit.domain.common.service.RedisService;
import com.E1i3.NoExit.domain.common.service.S3Service;
import com.E1i3.NoExit.domain.member.domain.Member;
import com.E1i3.NoExit.domain.member.domain.Role;
import com.E1i3.NoExit.domain.member.dto.MemberDetResDto;
import com.E1i3.NoExit.domain.member.dto.MemberListResDto;
import com.E1i3.NoExit.domain.member.dto.MemberRankingResDto;
import com.E1i3.NoExit.domain.member.dto.MemberSaveReqDto;
import com.E1i3.NoExit.domain.member.dto.MemberUpdateDto;
import com.E1i3.NoExit.domain.member.repository.MemberRepository;
import com.E1i3.NoExit.domain.notification.service.NotificationService;
import com.E1i3.NoExit.domain.owner.domain.Owner;
import com.E1i3.NoExit.domain.owner.repository.OwnerRepository;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final RedisService redisService;
	private final S3Service s3Service;
	private final PasswordEncoder passwordEncoder;

	private static final String AUTH_EMAIL_PREFIX = "EMAIL_CERTIFICATE : ";
	private final OwnerRepository ownerRepository;

	@Autowired
	public MemberService(MemberRepository memberRepository, RedisService redisService, S3Service s3Service,
		PasswordEncoder passwordEncoder,
		OwnerRepository ownerRepository) {
		this.memberRepository = memberRepository;
		this.redisService = redisService;
		this.s3Service = s3Service;
		this.passwordEncoder = passwordEncoder;
		this.ownerRepository = ownerRepository;
	}

	// 회원 등록
	@Transactional
	public Member memberCreate(MemberSaveReqDto memberSaveReqDto, MultipartFile imgFile) {
		// 레디스에 인증이 된 상태인지 확인
		String chkVerified = redisService.getValues(AUTH_EMAIL_PREFIX + memberSaveReqDto.getEmail());
		if (chkVerified == null || !chkVerified.equals("true")) {
			throw new IllegalStateException("이메일 인증이 필요합니다.");
		}

		// 이메일로 회원을 검색
		memberRepository.findByEmail(memberSaveReqDto.getEmail()).ifPresent(existingMember -> {
			throw new EntityExistsException("이미 존재하는 이메일입니다.");
		});
		String encodedPassword = passwordEncoder.encode(memberSaveReqDto.getPassword());
		String imageUrl = s3Service.uploadFile(imgFile, "member");
		return memberRepository.save(memberSaveReqDto.toEntity(encodedPassword, imageUrl));
	}

	// 회원 조회
	public Page<MemberListResDto> memberList(Pageable pageable) {
		Page<Member> memberList = memberRepository.findAll(pageable);
		return memberList.map(a -> a.fromEntity());
	}

	public Page<MemberRankingResDto> memberRankingList(Pageable pageable) { // 게시글 전체 조회
		Page<Member> memberList = memberRepository.findAll(pageable);
		return memberList.map(a -> a.rankingFromEntity());
	}

	// 회원 삭제
	@Transactional
	public Member memberDelete() {
		// 토큰을 통해 이메일 가져오기
		String email = getEmailFromToken();
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("존재하지 않는 이메일입니다."));
		return member.updateDelYN();
	}

	// 회원 정보 수정
	@Transactional
	public Member memberUpdate(MemberUpdateDto memberUpdateDto, MultipartFile imgFile) {
		// 이메일과 비밀번호는 바뀌지않음
		String email = getEmailFromToken();
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("존재하지 않는 이메일입니다."));
	    String encodedPassword = member.getPassword();
	    if (memberUpdateDto.getPassword() != null && !memberUpdateDto.getPassword().isBlank()) {
	        encodedPassword = passwordEncoder.encode(memberUpdateDto.getPassword());
	    }
	
	    String imageUrl;
	    if (imgFile != null && !imgFile.isEmpty()) {
	        imageUrl = s3Service.uploadFile(imgFile, "member");
	    } else {
	        imageUrl = member.getProfileImage();
	    }
	
	    return member.updateMember(memberUpdateDto, email, encodedPassword, imageUrl);
	}

	// 로그인
	public Object login(LoginReqDto loginReqDto) {
		if (loginReqDto.getRole().equals(Role.USER)) {
			Member member = memberRepository.findByEmail(loginReqDto.getEmail())
				.orElseThrow(() -> new EntityNotFoundException("존재하지 않는 이메일입니다."));
			// 	password 일치 여부
			if (!passwordEncoder.matches(loginReqDto.getPassword(), member.getPassword())) {
				throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
			}
			return member;
		} else if (loginReqDto.getRole().equals(Role.OWNER)) {
			Owner owner = ownerRepository.findByEmail(loginReqDto.getEmail())
				.orElseThrow(() -> new EntityNotFoundException("존재하지 않는 이메일입니다."));
			// 	password 일치 여부
			if (!passwordEncoder.matches(loginReqDto.getPassword(), owner.getPassword())) {
				throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
			}
			return owner;
		}
		return null;
	}

	// 회원 상세 조회
	public MemberDetResDto myInfo() {
		String memberEmail = SecurityContextHolder.getContext().getAuthentication().getName();
		Member member = memberRepository.findByEmail(memberEmail).orElseThrow(EntityNotFoundException::new);
		return member.detFromEntity();
	}

	public String getEmailFromToken() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
}
