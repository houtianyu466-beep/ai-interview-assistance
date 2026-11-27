/**
 * 视频面试（微信视频通话风格）相关类型。
 *
 * 后端契约：
 * - REST `/api/v1/video/*` 返回 `VideoRoomVO` 与 `{iceServers}`；
 * - WebSocket `/ws/call` 上所有消息统一为 `{ type, data }`。
 */

/** 通话模式：AI 模拟面试 / 真人 1:1 陪练 */
export type CallMode = 'AI' | 'HUMAN'

/** 后端 Long 型 id 在 JSON 里可能是数字，也可能被序列化为字符串 */
export type CallUserId = string | number

/** 视频房间状态；后端新增状态时按字符串透传，前端只做展示 */
export type VideoRoomStatus = 'CREATED' | 'WAITING' | 'ACTIVE' | 'ENDED' | (string & {})

/** `POST /v1/video/rooms` 的响应（AI 模式需带上练习会话 id） */
export interface VideoRoomVO {
  id: string
  code: string
  mode: CallMode
  practiceSessionId?: string | null
  hostUserId: CallUserId
  status: VideoRoomStatus
  createdAt?: string
}

/** `POST /v1/video/rooms` 的请求体 */
export interface CreateVideoRoomRequest {
  mode: CallMode
  practiceSessionId?: string
}

/** `/v1/video/ice` 中的单个 ICE 服务 */
export interface IceServer {
  urls: string | string[]
  username?: string | null
  credential?: string | null
}

/** `/v1/video/ice` 的响应 */
export interface IceConfig {
  iceServers: IceServer[]
}

/** 房间内成员（信令推送） */
export interface CallPeer {
  id: CallUserId
  peerId?: CallUserId
  userId?: CallUserId
  name: string
}

/** WebSocket 统一消息信封 */
export interface CallMessage<T = unknown> {
  type: CallMessageType
  data?: T
}

export type CallMessageType =
  // 服务端 → 客户端
  | 'ROOM_STATE'
  | 'PEER_JOINED'
  | 'PEER_LEFT'
  | 'OFFER'
  | 'ANSWER'
  | 'ICE'
  | 'AI_SAY'
  | 'AI_FINISHED'
  | 'CHAT'
  | 'ERROR'
  | 'PONG'
  // 客户端 → 服务端
  | 'AI_NEXT'
  | 'PING'
  | 'HANGUP'

/** `ROOM_STATE` 的 data */
export interface RoomStateData {
  code: string
  mode: CallMode
  selfId: CallUserId
  selfUserId?: CallUserId
  practiceSessionId?: string | null
  hostUserId: CallUserId
  peers: CallPeer[]
}

/** `PEER_JOINED` / `PEER_LEFT` 的 data */
export interface PeerEventData {
  id?: CallUserId
  peerId?: CallUserId
  userId?: CallUserId
  name: string
}

/** `OFFER` / `ANSWER` 的 data */
export interface SessionDescriptionData {
  sdp: RTCSessionDescriptionInit
  from?: CallUserId
}

/** `ICE` 的 data（candidate 为空表示候选结束，前端忽略） */
export interface IceCandidateData {
  candidate: RTCIceCandidateInit | null
  from?: CallUserId
}

/** `AI_SAY` 的 data */
export interface AiSayData {
  text: string
  questionId?: string
  seq?: number
  totalQuestions?: number
}

/** `AI_FINISHED` 的 data */
export interface AiFinishedData {
  practiceSessionId?: string
}

/** `CHAT` 的 data */
export interface ChatData {
  from?: CallUserId
  fromName?: string
  text: string
}

/** `ERROR` 的 data：后端可能发对象，也可能直接发字符串 */
export type CallErrorData = { message?: string } | string

/** 信令连接状态 */
export type CallSocketStatus =
  | 'idle'
  | 'connecting'
  | 'open'
  | 'reconnecting'
  | 'failed'
  | 'closed'

/** 状态切换的附加上下文（重连次数、下次重试间隔等） */
export interface CallSocketStatusDetail {
  attempt?: number
  retryInMs?: number
  reason?: string
  code?: number
}
