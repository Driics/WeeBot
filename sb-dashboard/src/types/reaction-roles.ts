// Reaction Roles

export type ReactionRoleMenuType = "REACTIONS" | "BUTTONS" | "BOTH";

// Response types
export interface ReactionRoleMenuResponse {
  id: number;
  guildId: string;
  channelId: string;
  messageId: string;
  title: string;
  description: string | null;
  menuType: ReactionRoleMenuType;
  createdAt: string;
  updatedAt: string | null;
  active: boolean;
  items: ReactionRoleMenuItemResponse[];
}

export interface ReactionRoleMenuItemResponse {
  id: number;
  menuId: number;
  guildId: string;
  groupId: number | null;
  roleId: string;
  emoji: string | null;
  label: string;
  description: string | null;
  displayOrder: number;
  toggleable: boolean;
  requiredRoleIds: string[] | null;
  active: boolean;
}

export interface ReactionRoleGroupResponse {
  id: number;
  guildId: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string | null;
  active: boolean;
}

export interface ReactionRoleMenuListResponse {
  menus: ReactionRoleMenuResponse[];
  total: number;
  page: number;
  size: number;
}

export interface ReactionRoleGroupListResponse {
  groups: ReactionRoleGroupResponse[];
  total: number;
  page: number;
  size: number;
}

// Request types
export interface CreateReactionRoleMenuRequest {
  title: string;
  description?: string;
  menuType: ReactionRoleMenuType;
  channelId: string;
  items?: CreateReactionRoleMenuItemRequest[];
}

export interface UpdateReactionRoleMenuRequest {
  title?: string;
  description?: string;
  menuType?: ReactionRoleMenuType;
  channelId?: string;
  active?: boolean;
}

export interface CreateReactionRoleMenuItemRequest {
  groupId?: number;
  roleId: string;
  emoji?: string;
  label: string;
  description?: string;
  displayOrder?: number;
  toggleable?: boolean;
  requiredRoleIds?: string[];
}

export interface UpdateReactionRoleMenuItemRequest {
  groupId?: number;
  roleId?: string;
  emoji?: string;
  label?: string;
  description?: string;
  displayOrder?: number;
  toggleable?: boolean;
  requiredRoleIds?: string[];
  active?: boolean;
}

export interface CreateReactionRoleGroupRequest {
  name: string;
  description?: string;
}

export interface UpdateReactionRoleGroupRequest {
  name?: string;
  description?: string;
  active?: boolean;
}

// Action response types
export interface ReactionRoleActionResponse {
  success: boolean;
  id: number | null;
  message: string;
}

export interface PostMenuResponse {
  success: boolean;
  messageId: string | null;
  message: string;
}
