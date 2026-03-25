package app.salary.rulepack.repository;

import app.salary.rulepack.entity.RulePackEntity;
import com.google.cloud.spring.data.firestore.FirestoreReactiveRepository;
import org.springframework.stereotype.Repository;

/**
 * Firestore repository for rule pack documents.
 * All filtering and querying is performed in the service layer using findAll()
 * with in-memory predicates, avoiding the need for composite Firestore indexes.
 */
@Repository
public interface RulePackRepository extends FirestoreReactiveRepository<RulePackEntity> {
}
