export const FIELD_OPTION_LABELS: Record<string, string> = {
  GUIDE: '指南',
  COURSE: '课程',
  TOOL: '工具',
  DOCUMENT: '文档',
  ARTICLE: '文章 / 经验',
  EXTERNAL_LINK: '外部链接',
  PROJECT: '项目',
  VIDEO: '视频',
  EXPERIENCE: '经验',
  ANNOUNCEMENT: '网站公告',
  EVENT: '活动通知',
  NOTE: '学习笔记',
  RECAP: '竞赛复盘',
  GENERAL: '通用分享',
  ORIGINAL: '原创',
  EXTERNAL: '外部来源',
}

export function fieldOptionLabel(option: string) {
  return FIELD_OPTION_LABELS[option] ?? option
}
