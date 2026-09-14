package com.ruoyi.web.controller.system;

import java.util.Map;
import org.dromara.x.file.storage.core.FileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.common.utils.file.XFileStorageUtils;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.domain.SysStorageFile;
import com.ruoyi.system.service.ISysStorageFileService;
import com.ruoyi.system.service.StorageFileAccessUrlBuilder;
import com.ruoyi.system.service.ISysUserService;

/**
 * 当前登录用户的个人中心接口
 * <p>
 * 个人资料、密码修改等流程保持若依原有逻辑；头像上传改为使用 X File Storage
 * 因此头像可以存储到本地、阿里云 OSS、MinIO，或后续新增的其它平台
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(SysProfileController.class);

    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    /**
     * 头像上传使用的统一文件存储适配器
     * <p>
     * 控制器只依赖该工具类，不直接依赖具体平台 SDK，后续新增平台时控制器无需改造
     */
    @Autowired
    private XFileStorageUtils xFileStorageUtils;

    /**
     * 存储文件记录服务
     * <p>
     * 头像上传成功后绑定新文件，替换旧头像时只标记待删除，由定时任务延迟清理
     */
    @Autowired
    private ISysStorageFileService storageFileService;

    /**
     * 文件对外访问地址构建器
     * <p>
     * 用户表保存真实头像 URL，接口返回时转换成公开域名或后端代理地址
     */
    @Autowired
    private StorageFileAccessUrlBuilder storageFileAccessUrlBuilder;

    /**
     * 编程式事务模板
     * <p>
     * 头像上传真实文件不参与数据库事务，只把用户头像更新、文件绑定和旧头像状态标记放入同一个事务
     */
    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * 头像业务类型
     */
    private static final String BIZ_TYPE_AVATAR = StorageFileConstants.BIZ_TYPE_AVATAR;

    /**
     * 头像缩略图默认宽度
     */
    private static final int AVATAR_THUMBNAIL_WIDTH = 100;

    /**
     * 头像缩略图默认高度
     */
    private static final int AVATAR_THUMBNAIL_HEIGHT = 100;

    /**
     * 查询当前登录用户的个人资料、角色组和岗位组
     *
     * @return 当前登录用户的个人资料
     */
    @GetMapping
    public AjaxResult profile()
    {
        // 从安全上下文中获取当前登录用户
        LoginUser loginUser = getLoginUser();
        // 个人中心直接展示当前令牌中的用户基础资料
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success(user);
        // 角色组和岗位组用于个人中心页面展示
        ajax.put("roleGroup", userService.selectUserRoleGroup(loginUser.getUsername()));
        ajax.put("postGroup", userService.selectUserPostGroup(loginUser.getUsername()));
        return ajax;
    }

    /**
     * 修改当前用户可编辑的个人资料字段
     * <p>
     * 手机号、邮箱唯一性仍交给用户服务校验，保证与用户管理模块的数据范围和唯一性规则一致
     *
     * @param user 前端提交的个人资料字段
     * @return 资料和登录缓存更新成功时返回成功
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult updateProfile(@RequestBody SysUser user)
    {
        // 获取当前登录用户，后续更新数据库和刷新缓存都基于同一个用户对象
        LoginUser loginUser = getLoginUser();
        SysUser currentUser = loginUser.getUser();
        // 只允许用户修改个人中心开放的字段
        currentUser.setNickName(user.getNickName());
        currentUser.setEmail(user.getEmail());
        currentUser.setPhonenumber(user.getPhonenumber());
        currentUser.setSex(user.getSex());
        // 手机号不为空时校验唯一性
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
        }
        // 邮箱不为空时校验唯一性
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
        }
        // 持久化成功后刷新登录用户缓存
        if (userService.updateUserProfile(currentUser) > 0)
        {
            // 同步登录用户缓存，避免数据库已更新但当前会话仍显示旧资料
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改个人信息异常，请联系管理员");
    }

    /**
     * 校验旧密码后修改当前用户密码
     *
     * @param params 参数键包括 oldPassword 和 newPassword
     * @return 密码和登录用户缓存更新成功时返回成功
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(@RequestBody Map<String, String> params)
    {
        // 从请求体中取旧密码和新密码
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        // 获取当前登录用户和用户主键
        LoginUser loginUser = getLoginUser();
        Long userId = loginUser.getUserId();
        // 从数据库读取最新密码，避免使用过期缓存
        SysUser user = userService.selectUserById(userId);
        String password = user.getPassword();
        // 旧密码必须匹配
        if (!SecurityUtils.matchesPassword(oldPassword, password))
        {
            return error("修改密码失败，旧密码错误");
        }
        // 新旧密码不能相同
        if (SecurityUtils.matchesPassword(newPassword, password))
        {
            return error("新密码不能与旧密码相同");
        }
        // 新密码入库前先加密
        newPassword = SecurityUtils.encryptPassword(newPassword);
        if (userService.resetUserPwd(userId, newPassword) > 0)
        {
            // 同步缓存中的密码更新时间，保证令牌中的用户状态与数据库一致
            loginUser.getUser().setPwdUpdateDate(DateUtils.getNowDate());
            loginUser.getUser().setPassword(newPassword);
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改密码异常，请联系管理员");
    }

    /**
     * 上传当前用户的新头像
     * <p>
     * platform 为空时使用配置中的默认存储平台；platform 不为空时上传到对应的已启用平台
     * 头像接口只允许图片扩展名
     *
     * @param file avatarfile 表单字段提交的头像文件
     * @param platform 可选存储平台标识，例如 local-1、aliyun-oss-1、minio-1
     * @return 头像访问 URL、缩略图 URL 和实际使用的存储平台
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file,
            @RequestParam(value = "platform", required = false) String platform) throws Exception
    {
        // 空文件直接走统一失败返回
        if (!file.isEmpty())
        {
            LoginUser loginUser = getLoginUser();
            // 工具类负责图片校验，并根据 platform 选择指定平台或默认平台
            // 头像默认生成缩略图，原图仍作为数据库头像地址保存
            FileInfo fileInfo = xFileStorageUtils.upload(file, platform, BIZ_TYPE_AVATAR, MimeTypeUtils.IMAGE_EXTENSION, true,
                    AVATAR_THUMBNAIL_WIDTH, AVATAR_THUMBNAIL_HEIGHT);
            // 数据库存储最终可访问的头像 URL
            String avatar = fileInfo.getUrl();
            // 先记录旧头像，事务提交成功后再处理历史本地文件删除
            String oldAvatar = loginUser.getUser().getAvatar();
            boolean avatarSaved;
            try
            {
                // 头像业务数据库动作放在同一事务里，避免用户表已更新但文件资产未绑定
                avatarSaved = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                {
                    // 更新用户头像字段，用户表仍保存最终可访问的原图 URL
                    if (!userService.updateUserAvatar(loginUser.getUserId(), avatar))
                    {
                        return false;
                    }
                    // 头像保存成功后把本次上传的新文件绑定到用户业务，避免被临时文件清理任务误删
                    storageFileService.bindStorageFile(avatar, BIZ_TYPE_AVATAR, String.valueOf(loginUser.getUserId()),
                            loginUser.getUsername());
                    if (StringUtils.isNotEmpty(oldAvatar) && !oldAvatar.startsWith(Constants.RESOURCE_PREFIX))
                    {
                        // X File Storage 上传的旧头像先标记待删除，交给定时任务延迟物理清理
                        storageFileService.markStorageFileWaitDelete(oldAvatar, BIZ_TYPE_AVATAR, loginUser.getUsername());
                    }
                    return true;
                }));
            }
            catch (Exception e)
            {
                // 数据库事务异常时立即补偿删除本次新头像，避免用户头像未保存但对象存储残留
                deleteUploadedAvatar(avatar);
                throw e;
            }
            if (avatarSaved)
            {
                if (StringUtils.isNotEmpty(oldAvatar) && oldAvatar.startsWith(Constants.RESOURCE_PREFIX))
                {
                    // 历史若依本地头像不参与数据库事务，等数据库提交成功后再删除磁盘文件
                    FileUtils.deleteFile(RuoYiConfig.getProfile() + FileUtils.stripPrefix(oldAvatar));
                }
                // 绑定后按 URL 读取文件资产记录，头像接口也同步返回 fileId、access 和对象 Key
                SysStorageFile storageFile = storageFileService.selectStorageFileByUrl(avatar);
                // 头像返回给前端时使用对外预览地址，避免私有或代理模式泄露真实存储 URL
                String displayAvatar = storageFile == null ? avatar : storageFileAccessUrlBuilder.buildPreviewUrl(storageFile);
                // 返回头像 URL 和实际平台，前端可据此展示当前上传去向
                AjaxResult ajax = AjaxResult.success();
                ajax.put("imgUrl", displayAvatar);
                ajax.put("platform", fileInfo.getPlatform());
                if (storageFile != null)
                {
                    // fileId 是后续前端预览、下载、业务绑定和后台排查的稳定文件资产主键
                    ajax.put("fileId", storageFile.getFileId());
                    // access 从文件记录表返回，前端可区分公开直连和私有授权下载
                    ajax.put("access", storageFile.getAccess());
                    // objectKey 是对象存储里的真实对象定位信息，避免业务只依赖 URL
                    ajax.put("objectKey", storageFile.getObjectKey());
                    // thObjectKey 对应缩略图对象定位信息
                    ajax.put("thObjectKey", storageFile.getThObjectKey());
                    // previewUrl 是头像对外预览地址，前端展示优先使用它
                    ajax.put("previewUrl", displayAvatar);
                    // downloadUrl 是头像下载地址，后续如果头像需要下载可直接复用
                    ajax.put("downloadUrl", storageFileAccessUrlBuilder.buildDownloadUrl(storageFile));
                    // thPreviewUrl 是头像缩略图对外预览地址
                    ajax.put("thPreviewUrl", storageFileAccessUrlBuilder.buildThumbnailPreviewUrl(storageFile));
                }
                // 返回缩略图地址，前端列表或头像小尺寸展示可优先使用
                ajax.put("thUrl", storageFile == null ? fileInfo.getThUrl() : storageFileAccessUrlBuilder.buildThumbnailPreviewUrl(storageFile));
                // 同步令牌缓存中的头像地址，前端无需重新登录即可看到新头像
                loginUser.getUser().setAvatar(avatar);
                tokenService.setLoginUser(loginUser);
                return ajax;
            }
            // 头像上传成功但用户表保存失败时删除新文件，避免产生无法关联的孤儿文件
            deleteUploadedAvatar(avatar);
        }
        return error("上传图片异常，请联系管理员");
    }

    /**
     * 删除已经上传但业务保存失败的新头像
     *
     * @param avatar 新头像 URL
     */
    private void deleteUploadedAvatar(String avatar)
    {
        try
        {
            // 这里删除的是本次刚上传的新文件，不影响当前登录用户原头像
            xFileStorageUtils.delete(avatar);
        }
        catch (Exception e)
        {
            // 补偿删除失败只记录日志，接口仍返回原业务失败信息
            log.warn("头像保存失败后删除新头像失败，avatar={}", avatar, e);
        }
    }
}
