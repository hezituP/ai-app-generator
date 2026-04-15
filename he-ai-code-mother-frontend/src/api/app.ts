import myAxios from '@/plugins/myAxios'

export interface AppAddRequest {
  appName?: string
  codeGenType: string
  initPrompt: string
}

export interface AppUpdateRequest {
  id: string
  appName: string
}

export interface AppAdminUpdateRequest {
  id: string
  appName?: string
  cover?: string
  priority?: number
}

export interface AppQueryRequest {
  pageNum: number
  pageSize: number
  appName?: string
  userId?: string
  priority?: number
  codeGenType?: string
}

export interface UserSimpleVO {
  id: string
  userName: string
  userAvatar: string
}

export interface AppVO {
  id: string
  appName: string
  cover: string
  initPrompt: string
  codeGenType: string
  deployKey: string
  deployedTime: string
  priority: number
  userId: string
  editTime: string
  createTime: string
  user?: UserSimpleVO
  userVO?: UserSimpleVO
}

export interface AppProjectFileVO {
  path: string
  content: string
}

export interface AppProjectSnapshotVO {
  codeGenType: string
  rootDirName: string
  previewUrl?: string
  entryFilePath?: string
  summary?: string
  files: AppProjectFileVO[]
}

export interface AgentStreamEvent {
  type: 'assistant' | 'assistant_delta' | 'status' | 'result' | 'error' | 'done'
  message: string
  data?: AppProjectSnapshotVO
}

export const resolveDeployUrl = (deployKey?: string) => {
  if (!deployKey) {
    return ''
  }
  if (/^https?:\/\//.test(deployKey)) {
    return deployKey
  }
  const baseURL = (myAxios.defaults.baseURL || '') as string
  return `${baseURL.replace(/\/$/, '')}/deploy/${deployKey}/`
}

export const addAppApi = (data: AppAddRequest) => myAxios.post('/app/add', data)
export const updateAppApi = (data: AppUpdateRequest) => myAxios.post('/app/update', data)
export const deleteAppApi = (id: string) => myAxios.post('/app/delete', { id })
export const getAppVOByIdApi = (id: string) => myAxios.get('/app/get/vo', { params: { id } })
export const getProjectSnapshotApi = (appId: string) => myAxios.get('/app/project/snapshot', { params: { appId } })
export const listMyAppVOByPageApi = (data: AppQueryRequest) => myAxios.post('/app/my/list/page/vo', data)
export const listGoodAppVOByPageApi = (data: AppQueryRequest) => myAxios.post('/app/good/list/page/vo', data)
export const deployAppApi = (appId: string) => myAxios.post('/app/deploy', { appId })
export const downloadAppCodeApi = (appId: string) => myAxios.get(`/app/download/${appId}`, { responseType: 'blob' })

export const adminDeleteAppApi = (id: string) => myAxios.post('/app/admin/delete', { id })
export const adminUpdateAppApi = (data: AppAdminUpdateRequest) => myAxios.post('/app/admin/update', data)
export const adminListAppVOByPageApi = (data: AppQueryRequest) => myAxios.post('/app/admin/list/page/vo', data)
