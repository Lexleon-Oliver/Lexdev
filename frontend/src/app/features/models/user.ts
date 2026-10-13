export interface User {
  id: number;
  username: string;
  fullName: string;
  email: string;
  role: string;
  active: boolean;
}

export interface UserCreateRequest {
  username: string;
  name: string;
  fullName: string;
  email: string;
  password: string;
  role: string;
}

export interface UserUpdateRequest {
  name: string;
  email: string;
  role: string;
}
