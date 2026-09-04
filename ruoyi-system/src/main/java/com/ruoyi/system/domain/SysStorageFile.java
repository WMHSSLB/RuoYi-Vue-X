package com.ruoyi.system.domain;

import java.time.LocalDateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 存储文件记录 sys_storage_file
 * <p>
 * 类名避免使用 SysFile，防止和若依服务监控模块的磁盘信息类产生 MyBatis 别名冲突
 *
 * @author ruoyi
 */
public class SysStorageFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long fileId;

    /** X File Storage 内部文件ID，由框架生成或由存储平台返回 */
    private String storageId;

    /** 原图访问地址 */
    private String url;

    /** 原图大小，单位字节 */
    private Long size;

    /** 原图文件名 */
    private String filename;

    /** 原始文件名 */
    private String originalFilename;

    /** 存储基础路径 */
    private String basePath;

    /** 文件路径 */
    private String path;

    /** 文件扩展名 */
    private String ext;

    /** 文件内容类型 */
    private String contentType;

    /** 存储平台 */
    private String platform;

    /** 缩略图访问地址 */
    private String thUrl;

    /** 缩略图文件名 */
    private String thFilename;

    /** 缩略图大小，单位字节 */
    private Long thSize;

    /** 缩略图内容类型 */
    private String thContentType;

    /** X File Storage 关联对象ID，框架扩展字段，不等同于本系统 bizId */
    private String objectId;

    /** X File Storage 关联对象类型，框架扩展字段，不等同于本系统 bizType */
    private String objectType;

    /** 原图存储平台元数据JSON，例如对象存储 Header 或自定义元信息 */
    private String metadata;

    /** 原图用户自定义元数据JSON，例如上传方传入的业务扩展元信息 */
    private String userMetadata;

    /** 缩略图存储平台元数据JSON */
    private String thMetadata;

    /** 缩略图用户自定义元数据JSON */
    private String thUserMetadata;

    /** X File Storage 附加属性JSON，只用于程序内部扩展，不会写入对象存储元数据 */
    private String attr;

    /** 原图访问控制ACL JSON，例如私有、公共读或平台专有权限配置 */
    private String fileAcl;

    /** 缩略图访问控制ACL JSON */
    private String thFileAcl;

    /** 原图哈希信息JSON，例如 MD5、SHA1、SHA256 等摘要结果 */
    private String hashInfo;

    /** 分片上传ID */
    private String uploadId;

    /** 分片上传状态 */
    private Integer uploadStatus;

    /** 文件状态（0临时 1已使用 2待删除 3已删除） */
    private String fileStatus;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 删除时间 */
    private LocalDateTime deleteTime;

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getStorageId()
    {
        return storageId;
    }

    public void setStorageId(String storageId)
    {
        this.storageId = storageId;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public Long getSize()
    {
        return size;
    }

    public void setSize(Long size)
    {
        this.size = size;
    }

    public String getFilename()
    {
        return filename;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public String getOriginalFilename()
    {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename)
    {
        this.originalFilename = originalFilename;
    }

    public String getBasePath()
    {
        return basePath;
    }

    public void setBasePath(String basePath)
    {
        this.basePath = basePath;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getExt()
    {
        return ext;
    }

    public void setExt(String ext)
    {
        this.ext = ext;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getThUrl()
    {
        return thUrl;
    }

    public void setThUrl(String thUrl)
    {
        this.thUrl = thUrl;
    }

    public String getThFilename()
    {
        return thFilename;
    }

    public void setThFilename(String thFilename)
    {
        this.thFilename = thFilename;
    }

    public Long getThSize()
    {
        return thSize;
    }

    public void setThSize(Long thSize)
    {
        this.thSize = thSize;
    }

    public String getThContentType()
    {
        return thContentType;
    }

    public void setThContentType(String thContentType)
    {
        this.thContentType = thContentType;
    }

    public String getObjectId()
    {
        return objectId;
    }

    public void setObjectId(String objectId)
    {
        this.objectId = objectId;
    }

    public String getObjectType()
    {
        return objectType;
    }

    public void setObjectType(String objectType)
    {
        this.objectType = objectType;
    }

    public String getMetadata()
    {
        return metadata;
    }

    public void setMetadata(String metadata)
    {
        this.metadata = metadata;
    }

    public String getUserMetadata()
    {
        return userMetadata;
    }

    public void setUserMetadata(String userMetadata)
    {
        this.userMetadata = userMetadata;
    }

    public String getThMetadata()
    {
        return thMetadata;
    }

    public void setThMetadata(String thMetadata)
    {
        this.thMetadata = thMetadata;
    }

    public String getThUserMetadata()
    {
        return thUserMetadata;
    }

    public void setThUserMetadata(String thUserMetadata)
    {
        this.thUserMetadata = thUserMetadata;
    }

    public String getAttr()
    {
        return attr;
    }

    public void setAttr(String attr)
    {
        this.attr = attr;
    }

    public String getFileAcl()
    {
        return fileAcl;
    }

    public void setFileAcl(String fileAcl)
    {
        this.fileAcl = fileAcl;
    }

    public String getThFileAcl()
    {
        return thFileAcl;
    }

    public void setThFileAcl(String thFileAcl)
    {
        this.thFileAcl = thFileAcl;
    }

    public String getHashInfo()
    {
        return hashInfo;
    }

    public void setHashInfo(String hashInfo)
    {
        this.hashInfo = hashInfo;
    }

    public String getUploadId()
    {
        return uploadId;
    }

    public void setUploadId(String uploadId)
    {
        this.uploadId = uploadId;
    }

    public Integer getUploadStatus()
    {
        return uploadStatus;
    }

    public void setUploadStatus(Integer uploadStatus)
    {
        this.uploadStatus = uploadStatus;
    }

    public String getFileStatus()
    {
        return fileStatus;
    }

    public void setFileStatus(String fileStatus)
    {
        this.fileStatus = fileStatus;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
    }

    public LocalDateTime getDeleteTime()
    {
        return deleteTime;
    }

    public void setDeleteTime(LocalDateTime deleteTime)
    {
        this.deleteTime = deleteTime;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("fileId", getFileId())
                .append("storageId", getStorageId())
                .append("url", getUrl())
                .append("platform", getPlatform())
                .append("filename", getFilename())
                .append("fileStatus", getFileStatus())
                .append("bizType", getBizType())
                .append("bizId", getBizId())
                .append("createTime", getCreateTime())
                .append("updateTime", getUpdateTime())
                .append("remark", getRemark())
                .toString();
    }
}
