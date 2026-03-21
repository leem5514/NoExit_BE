package com.E1i3.NoExit.domain.board.repository;

import com.E1i3.NoExit.domain.board.domain.BoardType;

import java.time.LocalDateTime;

public interface BoardListProjection {
    Long getId();
    String getWriter();
    String getTitle();
    int getBoardHits();
    int getLikes();
    BoardType getBoardType();
    LocalDateTime getCreatedTime();
    long getCommentCount();
    long getImageCount();
}
