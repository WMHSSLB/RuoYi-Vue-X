package com.ruoyi.system.domain;

import java.time.LocalDateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 存储文件分片记录 sys_storage_file_part
 * <p>
 * 用于承接 X File Storage 手动分片上传回调，和 sys_storage_file 主文件记录分开保存
 *
 * @author ruoyi
 */
public class SysStorageFilePart extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分片ID */
    private Long partId;

    /** X File Storage 内部分片ID，由框架生成或由存储平台返回 */
    private String storageId;

    /** 存储平台 */
    private String platform;

    /** 分片上传ID */
    private String uploadId;

    /** 分片编号 */
    private Integer partNumber;

    /** 分片大小，单位字节 */
    private Long partSize;

    /** 分片ETag，存储平台返回的分片唯一校验标识 */
    private String etag;

    /** 分片哈希信息JSON，例如 MD5、SHA1、SHA256 等摘要结果 */
    private String hashInfo;

    /** 分片最后修改时间 */
    private LocalDateTime lastModified;

    public Long getPartId()
    {
        return partId;
    }

    public void setPartId(Long partId)
    {
        this.partId = partId;
    }

    public String getStorageId()
    {
        return storageId;
    }

    public void setStorageId(String storageId)
    {
        this.storageId = storageId;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getUploadId()
    {
        return uploadId;
    }

    public void setUploadId(String uploadId)
    {
        this.uploadId = uploadId;
    }

    public Integer getPartNumber()
    {
        return partNumber;
    }

    public void setPartNumber(Integer partNumber)
    {
        this.partNumber = partNumber;
    }

    public Long getPartSize()
    {
        return partSize;
    }

    public void setPartSize(Long partSize)
    {
        this.partSize = partSize;
    }

    public String getEtag()
    {
        return etag;
    }

    public void setEtag(String etag)
    {
        this.etag = etag;
    }

    public String getHashInfo()
    {
        return hashInfo;
    }

    public void setHashInfo(String hashInfo)
    {
        this.hashInfo = hashInfo;
    }

    public LocalDateTime getLastModified()
    {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified)
    {
        this.lastModified = lastModified;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("partId", getPartId())
                .append("storageId", getStorageId())
                .append("platform", getPlatform())
                .append("uploadId", getUploadId())
                .append("partNumber", getPartNumber())
                .append("partSize", getPartSize())
                .append("etag", getEtag())
                .append("createTime", getCreateTime())
                .append("lastModified", getLastModified())
                .toString();
    }
}
