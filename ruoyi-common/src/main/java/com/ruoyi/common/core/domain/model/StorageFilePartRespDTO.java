package com.ruoyi.common.core.domain.model;

import java.util.Date;
import org.dromara.x.file.storage.core.upload.FilePartInfo;

/**
 * 存储文件分片返回 DTO
 * <p>
 * 用于统一分片上传、分片列表接口的返回字段
 *
 * @author 955Kane
 */
public class StorageFilePartRespDTO
{
    /**
     * 分片记录 ID
     */
    private String id;

    /**

     * 存储平台

     */
    private String platform;

    /**

     * 分片上传会话 ID

     */
    private String uploadId;

    /**

     * 分片编号，从 1 开始

     */
    private Integer partNumber;

    /**

     * 分片大小，单位字节

     */
    private Long partSize;

    /**

     * 存储平台返回的分片 ETag

     */
    private String etag;

    /**

     * 分片哈希信息

     */
    private Object hashInfo;

    /**

     * 创建时间

     */
    private Date createTime;

    /**

     * 最后修改时间

     */
    private Date lastModified;

    /**
     * 根据 X File Storage 分片信息构造返回 DTO
     *
     * @param filePartInfo X File Storage 分片信息
     * @return 分片返回 DTO
     */
    public static StorageFilePartRespDTO from(FilePartInfo filePartInfo)
    {
        StorageFilePartRespDTO resp = new StorageFilePartRespDTO();
        if (filePartInfo == null)
        {
            return resp;
        }
        // 分片字段直接来自框架，前端用 uploadId 和 partNumber 做断点续传匹配
        resp.setId(filePartInfo.getId());
        resp.setPlatform(filePartInfo.getPlatform());
        resp.setUploadId(filePartInfo.getUploadId());
        resp.setPartNumber(filePartInfo.getPartNumber());
        resp.setPartSize(filePartInfo.getPartSize());
        resp.setEtag(filePartInfo.getETag());
        resp.setHashInfo(filePartInfo.getHashInfo());
        resp.setCreateTime(filePartInfo.getCreateTime());
        resp.setLastModified(filePartInfo.getLastModified());
        return resp;
    }

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
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

    public Object getHashInfo()
    {
        return hashInfo;
    }

    public void setHashInfo(Object hashInfo)
    {
        this.hashInfo = hashInfo;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getLastModified()
    {
        return lastModified;
    }

    public void setLastModified(Date lastModified)
    {
        this.lastModified = lastModified;
    }
}
