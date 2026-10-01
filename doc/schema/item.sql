-- item 政务服务事项标准化管理与实施清单同源发布管理 -- schema (chen-017)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_017

CREATE TABLE IF NOT EXISTS t_item_alert_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '办件逾期风险判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '低界逾期率(百分比)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界逾期率(百分比)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界逾期率(百分比)',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '让线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='办件逾期风险判定线';

CREATE TABLE IF NOT EXISTS t_item_catalog_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '上级目录下发册次号',
  row_no int DEFAULT NULL COMMENT '原册行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被挑出的前置项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行待核要素项数',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已挂接 2挂起',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='上级目录下发册核对行';

CREATE TABLE IF NOT EXISTS t_item_catalog_ver (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '事项目录版次单号',
  stage int DEFAULT NULL COMMENT '当前档次 0..3（草拟/复核/付发布/已上柜）',
  status int DEFAULT NULL COMMENT '版次落点 0未起 1在办 2已作废',
  content varchar(255) DEFAULT NULL COMMENT '版次记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项目录条目版次单';

CREATE TABLE IF NOT EXISTS t_item_change_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '要素调整核签单号',
  item_code varchar(64) DEFAULT NULL COMMENT '事项码（三道口算件、市核对照只认它）',
  item_name varchar(128) DEFAULT NULL COMMENT '事项名号（仅记载，不作对照凭据）',
  before_fields varchar(2000) DEFAULT NULL COMMENT '改动前那几栏',
  after_fields varchar(2000) DEFAULT NULL COMMENT '改动后那几栏',
  change_reason varchar(500) DEFAULT NULL COMMENT '改的缘故',
  site_no varchar(64) DEFAULT NULL COMMENT '承办底册代号（前置检须命中在编底册）',
  arch_check int DEFAULT '0' COMMENT '承办底册前置检 0未过 1已过，未过则永久停县核',
  node_no int DEFAULT NULL COMMENT '当前所在道次 0..2（县核/市核/省定）；已核讫停在2',
  seq_no int DEFAULT NULL COMMENT '同事项当轮件序（当年第二件当场点名）',
  round_no int DEFAULT '1' COMMENT '重走轮次，首报为1，省定表述打回一次加一',
  sign_mode int DEFAULT NULL COMMENT '同口并签办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本口应落名数（由签迹点数得出）',
  sign_count int DEFAULT NULL COMMENT '本口已落名数（只按签迹表同一回点数回填）',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回 3已撤回',
  public_sync int DEFAULT '0' COMMENT '对外名录同步 0未同步 1已随省定章同步',
  locked int DEFAULT '0' COMMENT '全单封存 0在办 1省定落章后锁住',
  seal_time datetime DEFAULT NULL COMMENT '省定落章时间',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_round_seq (item_code, round_no, seq_no),
  KEY idx_bill_no (bill_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事项要素调整逐级核签单';

-- 一笔署名一行：口次+轮次+先后定位；沉底旧批 del_flag=1，不与新批并排。
CREATE TABLE IF NOT EXISTS t_item_change_bill_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '核签单主键',
  node_no int NOT NULL COMMENT '落名口次 0县核 1市核 2省定',
  round_no int NOT NULL COMMENT '落名时的重走轮次',
  seq int NOT NULL COMMENT '同一口内落名先后（从1起）',
  signer varchar(64) NOT NULL COMMENT '落名人（亲笔者本人）',
  watch_role int DEFAULT NULL COMMENT '盯项角色 1盯要素齐不齐 2盯上位名录合规矩',
  verdict int DEFAULT NULL COMMENT '结论 0不予通过 1通过 2表述不合规矩打回',
  comment_text varchar(500) DEFAULT NULL COMMENT '签注',
  sign_time datetime DEFAULT NULL COMMENT '落名时间',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1沉底',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_bill_round_node_seq (bill_id, round_no, node_no, seq),
  KEY idx_bill_node_round (bill_id, node_no, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核签单签迹';

CREATE TABLE IF NOT EXISTS t_item_check_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '核查派单号',
  due_at datetime DEFAULT NULL COMMENT '该动手那一日的止点时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前几日开口派单',
  content varchar(255) DEFAULT NULL COMMENT '事由与被派部门记要',
  status int DEFAULT NULL COMMENT '条目情形 0未开口 1已派出 2办不成',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='要素定期核查派单';

CREATE TABLE IF NOT EXISTS t_item_dept_arch (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '承办部门底册代号',
  site_name varchar(128) DEFAULT NULL COMMENT '部门全称',
  site_type varchar(32) DEFAULT NULL COMMENT '机构类别',
  road_name varchar(128) DEFAULT NULL COMMENT '落在哪一级政务区划(省—市—县)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单卡年办件量低界(件/年)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界(件/年)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界(件/年)',
  status int DEFAULT NULL COMMENT '底册情形 0在编 1已撤并',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='承办部门底册';

CREATE TABLE IF NOT EXISTS t_item_implement_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '实施清单配置卡号',
  node_no int DEFAULT NULL COMMENT '本卡版次序',
  site_id int DEFAULT NULL COMMENT '所属承办部门底册',
  site_no varchar(64) DEFAULT NULL COMMENT '所属部门底册代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡申报年办件量(件/年)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本卡承诺时限(工作日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版次序走）',
  grade_level int DEFAULT NULL COMMENT '年办件量折到的服务监督层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已冻住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实施清单配置卡';

-- 初始档案数据（两条种子底册：一条在编、一条已撤并）
-- t_item_dept_arch 两条种子底册：id=0 在编、id=1 已撤并。
-- 单卡年办件量三线上限取 300/1500/6000 件。
INSERT IGNORE INTO t_item_dept_arch (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZW00', '青屏市承办部门底册（市政务服务和数据管理局，在编）', '行政许可', '青屏省—青屏市—青屏县', 300.00, 1500.00, 6000.00, 0, 0, 'seed', NOW()),
       (1, 'ZW01', '青屏市承办部门底册（市住房和城乡建设管理局，已撤并）', '行政确认', '青屏省—青屏市—青屏县', 300.00, 1500.00, 6000.00, 1, 0, 'seed', NOW());

