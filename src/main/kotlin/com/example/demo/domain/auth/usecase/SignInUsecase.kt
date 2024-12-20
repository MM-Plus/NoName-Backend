package com.example.demo.domain.auth.usecase

import com.example.demo.domain.auth.dto.request.SignInRequest
import com.example.demo.domain.auth.dto.response.SignInResponse
import com.example.demo.domain.auth.entity.RefreshToken
import com.example.demo.domain.auth.repository.RefreshTokenRepository
import com.example.demo.domain.user.repository.UserRepository
import com.example.demo.global.exception.ExceptionEnum
import com.example.demo.global.exception.NoNameException
import com.example.demo.global.security.jwt.JwtProvider
import com.example.demo.global.security.jwt.JwtType
import com.example.demo.global.security.jwt.dto.JwtDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class SignInUsecase (
	private val userRepository: UserRepository,
	private val passwordEncoder: PasswordEncoder,
	private val jwtProvider: JwtProvider,
	private val refreshTokenRepository: RefreshTokenRepository
) {
	fun execute(signInRequest: SignInRequest): SignInResponse {
		val email = signInRequest.email
		val user = userRepository.findByEmail(email).orElseThrow {
			NoNameException(ExceptionEnum.NOT_FOUND_USER)
		}

		if(!user.isVerified){
			throw NoNameException(ExceptionEnum.NOT_VERIFIED_EMAIL)
		}

		val id = user.id
		requireNotNull(id) { "id cannot be null" }

		val rawPassword = signInRequest.password
		val encodedPassword = user.encodedPassword

		if(!passwordEncoder.matches(rawPassword, encodedPassword)){
			throw NoNameException(ExceptionEnum.WRONG_PASSWORD)
		}

		val accessToken = jwtProvider.generateToken(id.toString(), JwtType.ACCESS_TOKEN)
		val refreshToken = getRefreshTokenOrSave(id)

		return SignInResponse(
			accessToken = accessToken.token,
			accessTokenExpiredAt = accessToken.expiredAt,
			refreshToken = refreshToken.token,
			refreshTokenExpiredAt = refreshToken.expiredAt
		)
	}

	fun getRefreshTokenOrSave(id: UUID): JwtDetails {
		val refreshToken = refreshTokenRepository.findById(id)

		if(refreshToken.isEmpty){
			val newRefreshToken = jwtProvider.generateToken(id.toString(), JwtType.REFRESH_TOKEN)
			refreshTokenRepository.save(RefreshToken(
				id = id,
				refreshToken = newRefreshToken.token,
				expires = jwtProvider.refreshTokenExpires,
			))
			return newRefreshToken
		} else {
			return JwtDetails(
				token = refreshToken.get().refreshToken,
				expiredAt = LocalDateTime.now().plus(Duration.ofMillis(refreshToken.get().expires))
			)
		}
	}
}