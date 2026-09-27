-- topon 地名与界线管理 -- schema (chen-010)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_010

CREATE TABLE IF NOT EXISTS t_topon_bound_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '勘定基准代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '勘定基准名称',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '一档桩间平距上限(米)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档桩间平距上限(米)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档桩间平距上限(米)',
  eff_start datetime DEFAULT NULL COMMENT '启用日',
  eff_end datetime DEFAULT NULL COMMENT '交棒日(不含)',
  priority int DEFAULT NULL COMMENT '取用顺位(数值越大越优先)',
  status int DEFAULT NULL COMMENT '线的情形 0当家 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='界线勘定基准档线';

CREATE TABLE IF NOT EXISTS t_topon_check_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '定期检验派发单号',
  due_at datetime DEFAULT NULL COMMENT '本轮应检之期的日终截止时刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前派发的自然日数',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2派不出去',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='定期检验派发条目';

CREATE TABLE IF NOT EXISTS t_topon_district_doc (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '乡镇街道区划代码',
  site_name varchar(128) DEFAULT NULL COMMENT '乡镇街道基础档案名称',
  site_type varchar(32) DEFAULT NULL COMMENT '所属地貌类',
  road_name varchar(128) DEFAULT NULL COMMENT '坐落(省—市—区县)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '号段可编容量一档上限(个)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(个)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(个)',
  status int DEFAULT NULL COMMENT '档案情形 0在用 1已撤并',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='乡镇街道基础档案';

CREATE TABLE IF NOT EXISTS t_topon_marker_card (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '界桩标志补办事务卡号',
  stage int DEFAULT NULL COMMENT '当前格口 0..5',
  status int DEFAULT NULL COMMENT '卡的落定 0在办 1已成卷 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '勘办记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪格动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='界桩标志补办事务卡';

CREATE TABLE IF NOT EXISTS t_topon_name_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '命名更名核准单号',
  node_no int DEFAULT NULL COMMENT '当前所在层 0..3',
  sign_mode int DEFAULT NULL COMMENT '同层核准方式 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本层应落印人数',
  sign_count int DEFAULT NULL COMMENT '本层已落印人数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地名命名更名逐级核准单';

CREATE TABLE IF NOT EXISTS t_topon_plate_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '门楼牌号段卡号',
  site_id int DEFAULT NULL COMMENT '所属乡镇街道基础档案',
  site_no varchar(64) DEFAULT NULL COMMENT '乡镇街道区划代码',
  qty decimal(12,2) DEFAULT NULL COMMENT '单双侧可编号码总个数(个)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新编号码个数(个)',
  grade_level int DEFAULT NULL COMMENT '号段容量档',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已定档',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门楼牌号段建档卡';

CREATE TABLE IF NOT EXISTS t_topon_submit_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '地名数据汇交册号',
  row_no int DEFAULT NULL COMMENT '原册内行次',
  item_code varchar(64) DEFAULT NULL COMMENT '地名条目代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行填报地名条目数(条)',
  status int DEFAULT NULL COMMENT '行进展 0待销 1已入账 2挂驳回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地名数据汇交核收行';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- t_topon_district_doc 两条种子档案：id=0 在用、id=1 已撤并。
-- 号段可编容量上限取 40/200/600 个。
INSERT IGNORE INTO t_topon_district_doc (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'XZ00', '临川市城东区明德街道办事处基础档案', '城镇街区', '江南省—临川市—城东区', 40.00, 200.00, 600.00, 0, 0, 'seed', NOW()),
       (1, 'XZ01', '旧临川市城西乡人民政府基础档案（已撤并）', '毗界地段', '江南省—临川市—城西区', 40.00, 200.00, 600.00, 1, 0, 'seed', NOW());

