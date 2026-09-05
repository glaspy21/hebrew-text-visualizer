package com.hebrewproject.repository;

import com.hebrewproject.model.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, Long> {

    // All words in one verse, in correct reading order
    List<Word> findByVerse_IdOrderByPositionInVerse(Long verseId);

    // Every occurrence of a given root's raw Strong's ID, in canonical reading order.
    // @Query with JPQL here because "order by a field on a JOINED entity" isn't
    // cleanly expressible as a method name - this is exactly the kind of query
    // worth writing by hand rather than forcing into Spring's naming convention.
    @Query("SELECT w FROM Word w JOIN w.verse v " +
           "WHERE w.rootStrongIdRaw = :rootId " +
           "ORDER BY v.canonicalOrder, w.positionInVerse")
    List<Word> findAllByRootStrongIdInReadingOrder(@Param("rootId") String rootId);
}
