package com.E1i3.NoExit.domain.board.repository;

import com.E1i3.NoExit.domain.board.domain.Board;
import com.E1i3.NoExit.domain.board.domain.BoardType;
import com.E1i3.NoExit.domain.common.domain.DelYN;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BoardRepository extends JpaRepository<Board, Long> {

    @Query(
            value = """
                    select
                        b.id as id,
                        m.nickname as writer,
                        b.title as title,
                        b.boardHits as boardHits,
                        b.likes as likes,
                        b.boardType as boardType,
                        b.createdTime as createdTime,
                        (
                            select count(c)
                            from Comment c
                            where c.board = b
                              and c.delYN = com.E1i3.NoExit.domain.common.domain.DelYN.N
                        ) as commentCount,
                        (
                            select count(i)
                            from BoardImage i
                            where i.board = b
                              and i.delYN = com.E1i3.NoExit.domain.common.domain.DelYN.N
                        ) as imageCount
                    from Board b
                    join b.member m
                    where b.delYN = com.E1i3.NoExit.domain.common.domain.DelYN.N
                      and (:title is null or b.title like concat('%', :title, '%'))
                      and (:contents is null or b.contents like concat('%', :contents, '%'))
                      and (:boardType is null or b.boardType = :boardType)
                    """,
            countQuery = """
                    select count(b)
                    from Board b
                    where b.delYN = com.E1i3.NoExit.domain.common.domain.DelYN.N
                      and (:title is null or b.title like concat('%', :title, '%'))
                      and (:contents is null or b.contents like concat('%', :contents, '%'))
                      and (:boardType is null or b.boardType = :boardType)
                    """
    )
    Page<BoardListProjection> searchBoards(
            @Param("title") String title,
            @Param("contents") String contents,
            @Param("boardType") BoardType boardType,
            Pageable pageable
    );

    @Query("""
            select distinct b
            from Board b
            join fetch b.member m
            left join fetch b.comments c
            left join fetch c.member
            left join fetch b.imgs i
            where b.id = :id
            """)
    Optional<Board> findDetailBoardById(@Param("id") Long id);

    Page<Board> findByDelYN(Pageable pageable, DelYN delYN);
}
