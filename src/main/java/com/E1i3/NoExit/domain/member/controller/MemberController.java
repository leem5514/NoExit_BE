package com.E1i3.NoExit.domain.member.controller;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.swagger.annotations.Api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.E1i3.NoExit.domain.common.auth.JwtTokenProvider;
import com.E1i3.NoExit.domain.common.dto.CommonErrorDto;
import com.E1i3.NoExit.domain.common.dto.LoginReqDto;
import com.E1i3.NoExit.domain.member.domain.Member;
import com.E1i3.NoExit.domain.common.dto.CommonResDto;
import com.E1i3.NoExit.domain.member.domain.Role;
import com.E1i3.NoExit.domain.member.dto.MemberDetResDto;
import com.E1i3.NoExit.domain.member.dto.MemberListResDto;
import com.E1i3.NoExit.domain.member.dto.MemberRankingResDto;
import com.E1i3.NoExit.domain.member.dto.MemberSaveReqDto;
import com.E1i3.NoExit.domain.member.dto.MemberUpdateDto;
import com.E1i3.NoExit.domain.member.dto.memberRefreshDto;
import com.E1i3.NoExit.domain.member.service.MemberService;
import com.E1i3.NoExit.domain.notification.service.NotificationService;
import com.E1i3.NoExit.domain.owner.domain.Owner;
import com.E1i3.NoExit.domain.owner.service.OwnerService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController

@Api(tags = "회원 서비스")
public class MemberController {

	private final MemberService memberService;
	private final JwtTokenProvider jwtTokenProvider;
	private final RedisTemplate<String, Object> redisTemplate;

	@Value("${jwt.secretKeyRt}")
	private String secretKeyRt;

	@Autowired
	public MemberController(MemberService memberService, JwtTokenProvider jwtTokenProvider,
		RedisTemplate<String, Object> redisTemplate) {
		this.memberService = memberService;
		this.jwtTokenProvider = jwtTokenProvider;
		this.redisTemplate = redisTemplate;
	}

	// 회원가입 /member/create
	@Operation(summary = "[일반 사용자] 회원가입 API")
	@PostMapping("/member/create")
	public ResponseEntity<CommonResDto> memberCreatePost(@RequestPart(value = "data") MemberSaveReqDto dto,
		@RequestPart(value = "file") MultipartFile imgFile) {
		CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "회원가입 성공",
			memberService.memberCreate(dto, imgFile).getId());
		return new ResponseEntity<>(commonResDto, HttpStatus.OK);
	}

	// 상세 내역 수정
	// @Operation(summary = "[일반 사용자] 사용자 정보 수정 API")
	// @PostMapping("/member/update")
	// public ResponseEntity<CommonResDto> updateMember(@RequestBody MemberUpdateDto dto) {
	// 	CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "회원정보 수정", memberService.memberUpdate(dto).getId());
	// 	return new ResponseEntity<>(commonResDto, HttpStatus.OK);
	// }

	@Operation(summary = "[일반 사용자] 회원가입 API")
	@PostMapping("/member/update")
	public ResponseEntity<CommonResDto> memberCreatePost(@RequestPart(value = "data") MemberUpdateDto dto,
		@RequestPart(value = "file", required = false) MultipartFile imgFile) {
		CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "회원가입 성공",
			memberService.memberUpdate(dto, imgFile).getId());
		return new ResponseEntity<>(commonResDto, HttpStatus.OK);
	}

	// 탈퇴 (?)
	@Operation(summary = "[일반 사용자] 사용자 탈퇴 API")
	@PostMapping("/member/delete")
	public ResponseEntity<CommonResDto> deleteMember(@RequestBody MemberUpdateDto dto) {
		CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "회원정보 삭제", memberService.memberDelete());
		return new ResponseEntity<>(commonResDto, HttpStatus.OK);
	}

	// 회원 정보(마이페이지)
	@Operation(summary = "[일반 사용자] 마이페이지 API")
	@GetMapping("/member/myInfo")
	public ResponseEntity<Object> myInfo() {
		MemberDetResDto member = memberService.myInfo();
		return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "회원 정보 상세 조회", member), HttpStatus.OK);
	}

	@Operation(summary = "[사용자] 로그인 API")
	@PostMapping("/doLogin")
	public ResponseEntity<Object> doLogin(@RequestBody LoginReqDto loginReqDto) {
	    Map<String, Object> loginInfo = new HashMap<>();
	    Object user = memberService.login(loginReqDto);
	
	    if (user instanceof Member) {
	        Member member = (Member) user;
	        String jwtToken = jwtTokenProvider.createToken(member.getEmail(), member.getRole().toString());
	        String refreshToken = jwtTokenProvider.createRefreshToken(member.getEmail(), member.getRole().toString());
	
	        redisTemplate.opsForValue().set(member.getEmail(), refreshToken, expirationRt, TimeUnit.MINUTES);
	
	        loginInfo.put("id", member.getId());
	        loginInfo.put("token", jwtToken);
	        loginInfo.put("refreshToken", refreshToken);
	
	        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "일반 사용자 로그인 성공", loginInfo), HttpStatus.OK);
	    } else if (user instanceof Owner) {
	        Owner owner = (Owner) user;
	        String jwtToken = jwtTokenProvider.createToken(owner.getEmail(), owner.getRole().toString());
	        String refreshToken = jwtTokenProvider.createRefreshToken(owner.getEmail(), owner.getRole().toString());
	
	        redisTemplate.opsForValue().set(owner.getEmail(), refreshToken, expirationRt, TimeUnit.MINUTES);
	
	        loginInfo.put("id", owner.getId());
	        loginInfo.put("token", jwtToken);
	        loginInfo.put("refreshToken", refreshToken);
	
	        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "점주 로그인 성공", loginInfo), HttpStatus.OK);
	    }
	
	    throw new IllegalArgumentException("로그인 처리 중 오류가 발생했습니다.");
	}

	@PostMapping("/refresh-token")
	public ResponseEntity<?> generateNewAccesstoken(@RequestBody memberRefreshDto dto) {
		String rt = dto.getRefreshToken();
		Claims claims = null;
		try {
			// 코드를 통해 rt 검증
			claims = Jwts.parser().setSigningKey(secretKeyRt).parseClaimsJws(rt).getBody();
		} catch (Exception e) {
			return new ResponseEntity<>(new CommonErrorDto(HttpStatus.BAD_REQUEST, "invalids refresh token"),
				HttpStatus.BAD_REQUEST);
		}
		String email = claims.getSubject();
		String role = claims.get("role").toString();

		// redis를 조회하여 rt 추가 검증
		Object obj = redisTemplate.opsForValue().get(email);
		if (obj == null || !obj.toString().equals(rt)) {
			return new ResponseEntity<>(new CommonErrorDto(HttpStatus.UNAUTHORIZED, "invalids refresh token"),
				HttpStatus.UNAUTHORIZED);
		}
		String newAt = jwtTokenProvider.createToken(email, role);
		Map<String, Object> info = new HashMap<>();
		info.put("token", newAt);

		// 생성된 토큰을 comonResDto에 담아서 사용자에게 리턴
		return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "at is renewed", info), HttpStatus.OK);
	}

	@GetMapping("/member/ranking") // 게시글 전체 조회
	public ResponseEntity<Object> memberRankingList(
		@PageableDefault(size = 30, sort = "point", direction = Sort.Direction.DESC) Pageable pageable) {
		//         Page<BoardListResDto> dtos =  boardService.boardList(pageable);
		Page<MemberRankingResDto> memberRankingResDtos = memberService.memberRankingList(pageable);
		CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "list is successfully found", memberRankingResDtos);
		return new ResponseEntity<>(commonResDto, HttpStatus.OK);
	}
}
