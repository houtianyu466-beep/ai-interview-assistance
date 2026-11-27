package com.aiinterview.video.service;

import com.aiinterview.common.api.ErrorCode;
import com.aiinterview.common.exception.BizException;
import com.aiinterview.interview.entity.Interview;
import com.aiinterview.interview.mapper.InterviewMapper;
import com.aiinterview.security.LoginUser;
import com.aiinterview.video.config.VideoProperties;
import com.aiinterview.video.dto.CreateVideoRoomRequest;
import com.aiinterview.video.dto.VideoRoomVO;
import com.aiinterview.video.entity.VideoRoom;
import com.aiinterview.video.mapper.VideoRoomMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频房间服务单元测试：覆盖 AI 模式归属校验、房间码格式与结束房间幂等。
 */
@ExtendWith(MockitoExtension.class)
class VideoRoomServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PRACTICE_SESSION_ID = 100L;

    @Mock
    private VideoRoomMapper videoRoomMapper;

    @Mock
    private InterviewMapper interviewMapper;

    private VideoRoomService videoRoomService;

    /**
     * 构造被测服务，并写入当前登录用户。
     */
    @BeforeEach
    void setUp() {
        videoRoomService = new VideoRoomService(videoRoomMapper, interviewMapper, new VideoProperties());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(USER_ID, "alice", "CANDIDATE"),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
    }

    /**
     * 清理安全上下文，避免测试之间互相影响。
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("AI 模式关联本人练习会话时可创建房间")
    void shouldCreateAiRoomWhenPracticeSessionOwned() {
        when(interviewMapper.selectById(PRACTICE_SESSION_ID)).thenReturn(ownedPracticeSession());
        stubInsertSuccess();

        VideoRoomVO room = videoRoomService.create(new CreateVideoRoomRequest("AI", PRACTICE_SESSION_ID));

        assertThat(room.mode()).isEqualTo(VideoRoom.MODE_AI);
        assertThat(room.practiceSessionId()).isEqualTo(PRACTICE_SESSION_ID);
        assertThat(room.hostUserId()).isEqualTo(USER_ID);
        assertThat(room.status()).isEqualTo(VideoRoom.STATUS_WAITING);
    }

    @Test
    @DisplayName("AI 模式关联他人练习会话时返回 403")
    void shouldRejectAiModeWithForeignPracticeSession() {
        Interview practiceSession = new Interview();
        practiceSession.setId(PRACTICE_SESSION_ID);
        practiceSession.setUserId(USER_ID + 1);
        when(interviewMapper.selectById(PRACTICE_SESSION_ID)).thenReturn(practiceSession);

        assertThatThrownBy(() -> videoRoomService.create(new CreateVideoRoomRequest("AI", PRACTICE_SESSION_ID)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("无权");
    }

    @Test
    @DisplayName("AI 模式缺少 practiceSessionId 时返回 400")
    void shouldRejectAiModeWithoutPracticeSessionId() {
        assertThatThrownBy(() -> videoRoomService.create(new CreateVideoRoomRequest("AI", null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST))
                .hasMessageContaining("practiceSessionId");
    }

    @Test
    @DisplayName("房间不存在时返回 404")
    void shouldRejectAiModeWhenPracticeSessionMissing() {
        when(interviewMapper.selectById(PRACTICE_SESSION_ID)).thenReturn(null);

        assertThatThrownBy(() -> videoRoomService.create(new CreateVideoRoomRequest("AI", PRACTICE_SESSION_ID)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessageContaining("练习会话不存在");
    }

    @Test
    @DisplayName("房间码为 8 位且排除 0/O/1/I")
    void shouldGenerateEightCharCodeWithoutAmbiguousCharacters() {
        stubInsertSuccess();

        VideoRoomVO room = videoRoomService.create(new CreateVideoRoomRequest("HUMAN", null));

        assertThat(room.code()).hasSize(8).matches("[A-HJ-NP-Z2-9]{8}");
        assertThat(room.mode()).isEqualTo(VideoRoom.MODE_HUMAN);
        assertThat(room.hostUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("房间码冲突后重新生成并成功插入")
    void shouldRetryWhenRoomCodeAlreadyExists() {
        when(videoRoomMapper.selectCount(any())).thenReturn(1L, 0L);
        when(videoRoomMapper.insert(any(VideoRoom.class))).thenAnswer(invocation -> {
            VideoRoom entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        });

        VideoRoomVO room = videoRoomService.create(new CreateVideoRoomRequest("HUMAN", null));

        assertThat(room.id()).isEqualTo(2L);
        verify(videoRoomMapper, times(2)).selectCount(any());
    }

    @Test
    @DisplayName("结束房间幂等，重复调用只更新一次")
    void shouldEndRoomIdempotently() {
        VideoRoom room = waitingRoom();
        when(videoRoomMapper.selectOne(any())).thenReturn(room);

        VideoRoomVO first = videoRoomService.end("ABCDEFGH", USER_ID);
        VideoRoomVO second = videoRoomService.end("abcdefgh", USER_ID);

        assertThat(first.status()).isEqualTo(VideoRoom.STATUS_ENDED);
        assertThat(room.getEndedAt()).isNotNull();
        assertThat(second.status()).isEqualTo(VideoRoom.STATUS_ENDED);
        verify(videoRoomMapper, times(1)).updateById(any(VideoRoom.class));
    }

    @Test
    @DisplayName("非房主结束房间返回 403")
    void shouldRejectEndByNonHost() {
        when(videoRoomMapper.selectOne(any())).thenReturn(waitingRoom());

        assertThatThrownBy(() -> videoRoomService.end("ABCDEFGH", USER_ID + 1))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                .hasMessageContaining("房主");
    }

    /**
     * 构造属于当前用户的练习会话。
     *
     * @return 练习会话实体
     */
    private Interview ownedPracticeSession() {
        Interview interview = new Interview();
        interview.setId(PRACTICE_SESSION_ID);
        interview.setUserId(USER_ID);
        return interview;
    }

    /**
     * 构造一个等待中的真人房间。
     *
     * @return 房间实体
     */
    private VideoRoom waitingRoom() {
        VideoRoom room = new VideoRoom();
        room.setId(1L);
        room.setCode("ABCDEFGH");
        room.setMode(VideoRoom.MODE_HUMAN);
        room.setHostUserId(USER_ID);
        room.setStatus(VideoRoom.STATUS_WAITING);
        return room;
    }

    /**
     * 桩：房间码预检查无冲突，插入成功并回填 id。
     */
    private void stubInsertSuccess() {
        when(videoRoomMapper.selectCount(any())).thenReturn(0L);
        when(videoRoomMapper.insert(any(VideoRoom.class))).thenAnswer(invocation -> {
            VideoRoom entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        });
    }
}
