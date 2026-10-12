-- Presentation data only; existing content is unchanged.
CREATE TABLE resource_config_versions (
 version BIGINT PRIMARY KEY, config_json JSON NOT NULL, actor_id CHAR(26) NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE resource_config_state (
 id INT PRIMARY KEY, current_version BIGINT NOT NULL,
 CONSTRAINT fk_resource_config_version FOREIGN KEY (current_version) REFERENCES resource_config_versions(version)
);
INSERT INTO resource_config_versions(version, config_json) VALUES (1, '{"version": 1, "categories": [{"key": "GENERAL", "label": "通用分享", "enabled": true}, {"key": "GUIDE", "label": "指南", "enabled": true}, {"key": "COURSE", "label": "课程", "enabled": true}, {"key": "TOOL", "label": "工具", "enabled": true}, {"key": "DOCUMENT", "label": "文档", "enabled": true}, {"key": "ARTICLE", "label": "文章", "enabled": true}, {"key": "EXTERNAL_LINK", "label": "外部链接", "enabled": true}, {"key": "PROJECT", "label": "项目", "enabled": true}, {"key": "VIDEO", "label": "视频", "enabled": true}, {"key": "EXPERIENCE", "label": "经验", "enabled": true}, {"key": "ANNOUNCEMENT", "label": "网站公告", "enabled": true}, {"key": "EVENT", "label": "活动通知", "enabled": true}, {"key": "NOTE", "label": "学习笔记", "enabled": true}, {"key": "RECAP", "label": "竞赛复盘", "enabled": true}], "fields": [{"key": "eventDate", "label": "活动日期", "type": "date", "help": "仅展示，不自动关闭或下架内容。", "options": [], "enabled": true}], "templates": [{"key": "general", "label": "通用分享", "markdown": "# 通用分享\\n\\n## 说明\\n\\n", "fieldKeys": [], "enabled": true}, {"key": "announcement", "label": "网站公告", "markdown": "# 网站公告\\n\\n## 说明\\n\\n", "fieldKeys": [], "enabled": true}, {"key": "event", "label": "活动通知", "markdown": "# 活动通知\\n\\n## 说明\\n\\n", "fieldKeys": ["eventDate"], "enabled": true}, {"key": "note", "label": "学习笔记", "markdown": "# 学习笔记\\n\\n## 说明\\n\\n", "fieldKeys": [], "enabled": true}, {"key": "recap", "label": "竞赛复盘", "markdown": "# 竞赛复盘\\n\\n## 说明\\n\\n", "fieldKeys": [], "enabled": true}]}');
INSERT INTO resource_config_state(id, current_version) VALUES (1, 1);
