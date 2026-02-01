package io.luminar.ledger.infrastructure.projection;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class ProjectionCheckpointRepository {
	private final EntityManager entityManager;

	public ProjectionCheckpointRepository(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager is required");
	}

	public long lockAndGetLastSequenceNumber(String projectionType) {
		Objects.requireNonNull(projectionType, "projectionType is required");

		entityManager.createNativeQuery(
				"insert into projection_checkpoints (projection_type, last_sequence_number) values (:projectionType, 0) " +
						"on conflict (projection_type) do nothing")
				.setParameter("projectionType", projectionType)
				.executeUpdate();

		Number last = (Number) entityManager.createNativeQuery(
				"select last_sequence_number from projection_checkpoints where projection_type = :projectionType for update")
				.setParameter("projectionType", projectionType)
				.getSingleResult();

		return last.longValue();
	}

	public void updateLastSequenceNumber(String projectionType, long lastSequenceNumber) {
		Objects.requireNonNull(projectionType, "projectionType is required");

		entityManager.createNativeQuery(
				"update projection_checkpoints set last_sequence_number = :lastSequenceNumber, updated_at = now() " +
						"where projection_type = :projectionType")
				.setParameter("lastSequenceNumber", lastSequenceNumber)
				.setParameter("projectionType", projectionType)
				.executeUpdate();
	}
}
