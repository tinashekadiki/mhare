export type EmhareEnvironment = {
  name: string
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error' | 'info'
}

export type EmhareNavigationItem = {
  label: string
  icon?: string
  to?: string
  badge?: string | number
  children?: EmhareNavigationItem[]
}

export type EmhareNavigationGroup = {
  label?: string
  icon?: string
  items: EmhareNavigationItem[]
}

export type EmhareQuickAction = {
  id: string
  label: string
  icon?: string
  description?: string
  to?: string
}

export type EmhareAcademicPeriod = {
  id: string
  label: string
  description?: string
  current?: boolean
}

export type EmhareNotification = {
  id: string
  title: string
  description?: string
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error' | 'info'
  time?: string
  readAt?: string | null
  version?: number
}

export type EmhareBreadcrumbItem = {
  label: string
  to?: string
}

export type EmhareDataTableColumn = {
  key: string
  label: string
  sortable?: boolean
  filterable?: boolean
  hidden?: boolean
  frozen?: boolean
  width?: string
  align?: 'left' | 'center' | 'right'
  editable?: boolean
  total?: boolean
}

export type EmhareDataTableSort = {
  key: string
  direction: 'asc' | 'desc'
}

export type EmhareDataTableFilter = {
  key: string
  value: string | number | boolean | null
}

export type EmhareDataTableState = {
  page: number
  pageSize: number
  search?: string
  sort?: EmhareDataTableSort[]
  filters?: EmhareDataTableFilter[]
  visibleColumns?: string[]
  selectedKeys?: Array<string | number>
  savedViewId?: string
}

export type EmhareDataTableAction = {
  id: string
  label: string
  icon?: string
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error' | 'info'
}

export type EmhareDataTableSavedView = {
  id: string
  label: string
  state: EmhareDataTableState
}

export type EmhareDataTableExportOption = {
  id: 'csv' | 'excel' | 'pdf' | 'print' | string
  label: string
  icon?: string
}

export type EmhareStatusTone = 'neutral' | 'primary' | 'success' | 'warning' | 'error' | 'info'
