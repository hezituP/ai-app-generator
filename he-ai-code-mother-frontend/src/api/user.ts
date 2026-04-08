import myAxios from '@/plugins/myAxios'

export interface UserRegisterRequest {
  userAccount: string
  userPassword: string
  checkPassword: string
}
export interface UserLoginRequest {
  userAccount: string
  userPassword: string
}
export interface LoginUserVO {
  id: string
  userName: string
  userAvatar: string
  userProfile: string
  userRole: string
  createTime: string
}
export interface UserVO {
  id: string
  userName: string
  userAvatar: string
  userProfile: string
  userRole: string
  createTime: string
}

export interface UserUpdateMyRequest {
  userName: string
}

export const userRegisterApi = (data: UserRegisterRequest) => myAxios.post('/user/register', data)
export const userLoginApi = (data: UserLoginRequest) => myAxios.post('/user/login', data)
export const userLogoutApi = () => myAxios.post('/user/logout')
export const getLoginUserApi = () => myAxios.get('/user/get/login')
export const updateMyUserApi = (data: UserUpdateMyRequest) => myAxios.post('/user/update/my', data)
