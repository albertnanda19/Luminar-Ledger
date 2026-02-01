package io.luminar.ledger.application.account;

import io.luminar.ledger.api.dto.response.TransactionHistoryItem;
import io.luminar.ledger.api.query.AccountTransactionHistoryQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountTransactionHistoryReadService {
	private static final Logger log = LoggerFactory.getLogger(AccountTransactionHistoryReadService.class);
	private static final TypeReference<List<TransactionHistoryItem>> TRANSACTION_HISTORY_ITEM_LIST = new TypeReference<>() {
	};
	private static final String ORDER_BY = "p.occurred_at asc, p.sequence_number asc, p.transaction_id asc, p.direction asc";

	private final AccountTransactionHistoryQuery accountTransactionHistoryQuery;
	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;
	private final Duration ttl;

	public AccountTransactionHistoryReadService(
			AccountTransactionHistoryQuery accountTransactionHistoryQuery,
			StringRedisTemplate stringRedisTemplate,
			ObjectMapper objectMapper,
			@Value("${ledger.cache.transaction-history.ttl-seconds:60}") int ttlSeconds) {
		this.accountTransactionHistoryQuery = Objects.requireNonNull(accountTransactionHistoryQuery,
				"AccountTransactionHistoryReadService.accountTransactionHistoryQuery is required");
		this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate,
				"AccountTransactionHistoryReadService.stringRedisTemplate is required");
		this.objectMapper = Objects.requireNonNull(objectMapper,
				"AccountTransactionHistoryReadService.objectMapper is required");
		this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
	}

	public List<TransactionHistoryItem> findByAccountId(UUID accountId, Instant from, Instant to, int page, int size) {
		Objects.requireNonNull(accountId, "accountId is required");
		int safePage = Math.max(0, page);
		int safeSize = Math.max(1, size);

		String filterHash = computeFilterHash(from, to, ORDER_BY);
		String cacheKey = "transaction-history::" + accountId + "::" + safePage + "::" + safeSize + "::" + filterHash;

		List<TransactionHistoryItem> cached = tryGet(cacheKey);
		if (cached != null) {
			return cached;
		}

		List<TransactionHistoryItem> result = accountTransactionHistoryQuery.findByAccountId(accountId, from, to,
				safePage,
				safeSize);
		trySet(cacheKey, result);
		return result;
	}

	private List<TransactionHistoryItem> tryGet(String cacheKey) {
		String key = Objects.requireNonNull(cacheKey, "cacheKey is required");
		try {
			String json = stringRedisTemplate.opsForValue().get(key);
			if (json == null || json.isBlank()) {
				return null;
			}
			return objectMapper.readValue(json, TRANSACTION_HISTORY_ITEM_LIST);
		} catch (Exception e) {
			log.warn("Transaction history cache read failed. Falling back to DB. key={}", key, e);
			return null;
		}
	}

	private void trySet(String cacheKey, List<TransactionHistoryItem> value) {
		String key = Objects.requireNonNull(cacheKey, "cacheKey is required");
		try {
			String json = Objects.requireNonNull(objectMapper.writeValueAsString(
					Objects.requireNonNull(value, "value is required")), "serialized json is required");
			stringRedisTemplate.opsForValue().set(key, json, Objects.requireNonNull(ttl, "ttl is required"));
		} catch (Exception e) {
			log.warn("Transaction history cache write failed. Continuing without cache. key={}", key, e);
		}
	}

	private static String computeFilterHash(Instant from, Instant to, String orderBy) {
		String canonical = "from=" + (from == null ? "" : from.toString()) + "|to=" + (to == null ? "" : to.toString())
				+ "|orderBy=" + Objects.requireNonNull(orderBy, "orderBy is required");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to compute transaction history filter hash", e);
		}
	}
}
