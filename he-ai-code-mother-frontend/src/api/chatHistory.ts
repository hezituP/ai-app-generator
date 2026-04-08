import myAxios from '@/plugins/myAxios'

export interface ChatHistoryItem {
  id: string
  message: string
  messageType: 'user' | 'ai' | 'error'
  appId: string
  userId: string
  createTime: string
  updateTime: string
}

export const listChatHistoryApi = (appId: string, pageSize = 50) =>
  myAxios.get('/chatHistory/list', {
    params: {
      appId,
      pageSize,
    },
  })
