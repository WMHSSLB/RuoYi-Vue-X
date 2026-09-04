-- ----------------------------
-- 存储文件记录表
-- ----------------------------
drop table if exists sys_storage_file;
create table sys_storage_file (
  file_id           bigint(20)       not null auto_increment    comment '文件ID',
  storage_id        varchar(64)      default null               comment 'X File Storage内部文件ID',
  url               varchar(1000)    not null                   comment '原图访问地址',
  size              bigint(20)       default 0                  comment '原图大小，单位字节',
  filename          varchar(255)     default ''                 comment '原图文件名',
  original_filename varchar(255)     default ''                 comment '原始文件名',
  base_path         varchar(255)     default ''                 comment '存储基础路径',
  path              varchar(255)     default ''                 comment '文件路径',
  ext               varchar(32)      default ''                 comment '扩展名',
  content_type      varchar(128)     default ''                 comment 'Content-Type',
  platform          varchar(64)      default ''                 comment '存储平台',
  th_url            varchar(1000)    default null               comment '缩略图访问地址',
  th_filename       varchar(255)     default ''                 comment '缩略图文件名',
  th_size           bigint(20)       default 0                  comment '缩略图大小，单位字节',
  th_content_type   varchar(128)     default ''                 comment '缩略图Content-Type',
  object_id         varchar(64)      default ''                 comment 'X File Storage关联对象ID',
  object_type       varchar(64)      default ''                 comment 'X File Storage关联对象类型',
  metadata          text                                        comment '原图存储平台元数据JSON',
  user_metadata     text                                        comment '原图用户自定义元数据JSON',
  th_metadata       text                                        comment '缩略图存储平台元数据JSON',
  th_user_metadata  text                                        comment '缩略图用户自定义元数据JSON',
  attr              text                                        comment 'X File Storage附加属性JSON',
  file_acl          text                                        comment '原图访问控制ACL JSON',
  th_file_acl       text                                        comment '缩略图访问控制ACL JSON',
  hash_info         text                                        comment '原图哈希信息JSON',
  upload_id         varchar(255)     default ''                 comment '分片上传ID',
  upload_status     int(1)           default null               comment '分片上传状态',
  file_status       char(1)          default '0'                comment '文件状态（0临时 1已使用 2待删除 3已删除）',
  biz_type          varchar(64)      default ''                 comment '业务类型',
  biz_id            varchar(64)      default ''                 comment '业务ID',
  delete_time       datetime                                   comment '删除时间',
  create_by         varchar(64)      default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)      default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)     default null               comment '备注',
  primary key (file_id),
  unique key uk_sys_storage_file_url (url(191)),
  key idx_sys_storage_file_status_time (file_status, create_time),
  key idx_sys_storage_file_biz (biz_type, biz_id)
) engine=innodb auto_increment=1 comment = '存储文件记录表';

-- ----------------------------
-- 存储文件分片记录表
-- ----------------------------
drop table if exists sys_storage_file_part;
create table sys_storage_file_part (
  part_id           bigint(20)       not null auto_increment    comment '分片ID',
  storage_id        varchar(64)      default null               comment 'X File Storage内部分片ID',
  platform          varchar(64)      default ''                 comment '存储平台',
  upload_id         varchar(255)     not null                   comment '分片上传ID',
  part_number       int(11)          not null                   comment '分片编号',
  part_size         bigint(20)       default 0                  comment '分片大小，单位字节',
  etag              varchar(255)     default ''                 comment '分片ETag，存储平台返回的分片唯一校验标识',
  hash_info         text                                        comment '分片哈希信息JSON',
  create_time       datetime                                   comment '创建时间',
  last_modified     datetime                                   comment '分片最后修改时间',
  primary key (part_id),
  unique key uk_sys_storage_file_part_upload_part (upload_id, part_number),
  key idx_sys_storage_file_part_upload_id (upload_id)
) engine=innodb auto_increment=1 comment = '存储文件分片记录表';

-- ----------------------------
-- 存储文件清理任务
-- ----------------------------
delete from sys_job where job_name = '清理存储文件' and job_group = 'SYSTEM';
insert into sys_job values(4, '清理存储文件', 'SYSTEM', 'storageFileTask.clearStorageFile()', '0 0 * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '清理超过配置时间未绑定业务或已经到期的待删除文件');
