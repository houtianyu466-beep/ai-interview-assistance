import { get, post } from '@/api/http'
import type { CreateVideoRoomRequest, IceConfig, VideoRoomVO } from '@/types/video'

const BASE = '/v1/video'

/**
 * 创建视频房间。
 *
 * AI 模式必须带上 `practiceSessionId`（先用 /v1/practice/sessions 建好练习会话）；
 * HUMAN 模式只传 `mode`，创建后把 6-8 位房间码分享给对方即可。
 */
export function createVideoRoom(body: CreateVideoRoomRequest): Promise<VideoRoomVO> {
  return post<VideoRoomVO>(`${BASE}/rooms`, body)
}

/** 按房间码查询房间（进入通话页时校验房间是否存在、是否已结束） */
export function fetchVideoRoom(code: string): Promise<VideoRoomVO> {
  return get<VideoRoomVO>(`${BASE}/rooms/${encodeURIComponent(code)}`)
}

/** 结束房间（挂断 / 离开 / 复盘跳转前调用，失败不影响前端离场） */
export function endVideoRoom(code: string): Promise<VideoRoomVO> {
  return post<VideoRoomVO>(`${BASE}/rooms/${encodeURIComponent(code)}/end`)
}

/** 获取 ICE 服务配置（STUN / TURN，credential 可能为 null） */
export function fetchIceConfig(): Promise<IceConfig> {
  return get<IceConfig>(`${BASE}/ice`)
}
