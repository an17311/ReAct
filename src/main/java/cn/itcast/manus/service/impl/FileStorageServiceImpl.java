package cn.itcast.manus.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.itcast.manus.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

/**
 * {@link FileStorageService} 实现：基于本地文件系统的存储与链接生成。
 * <p>以 UUID 为文件名写入 {@code file.base} 目录，并结合配置域名拼接下载/预览 URL。</p>
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    // Tika 用于自动识别文件的 MIME 类型
    private final Tika tika = new Tika();

    // 文件存放基础目录
    @Value("${file.base:/tmp/}")
    private String baseFolder;
    // 对外访问的域名前缀（用于拼接可访问的下载/预览链接）
    @Value("${file.domain:http://localhost:18081}")
    private String domain;

    /**
     * 保存字节数据到本地文件，文件名使用生成的 UUID。
     *
     * @param data 文件字节数据
     * @return 文件唯一标识（UUID）
     */
    @Override
    public String saveFile(byte[] data) {
        var uuid = IdUtil.fastSimpleUUID();
        var path = getPath(uuid);
        // 写数据到文件中
        FileUtil.writeBytes(data, path);
        return uuid;
    }

    /**
     * 拼接文件下载链接（带原始文件名参数）。
     *
     * @param name 文件原始名称
     * @param uuid 文件唯一标识
     * @return 完整下载 URL
     */
    @Override
    public String generateDownloadUrl(String name, String uuid) {
        return StrUtil.format("{}{}?name={}",
                this.domain,
                StrUtil.format(DOWNLOAD_PATH, Map.of("uuid", uuid)),
                name);
    }

    /**
     * 拼接文件在线预览/打开链接。
     *
     * @param uuid 文件唯一标识
     * @return 完整预览 URL
     */
    @Override
    public String generateOpenUrl(String uuid) {
        return StrUtil.format("{}{}",
                this.domain,
                StrUtil.format(OPEN_PATH, Map.of("uuid", uuid)));
    }

    /**
     * 根据 UUID 读取文件，并自动识别媒体类型，组装为可下载内容。
     *
     * @param uuid 文件唯一标识
     * @return 包含媒体类型与输入流的内容对象
     */
    @Override
    public DownloadTableContent generateDownloadableContent(String uuid) {
        var path = getPath(uuid);
        // 读取文件为字节流
        var is = FileUtil.getInputStream(path);

        try {
            // 识别文件类型
            var type = this.tika.detect(is);
            var mediaType = new MediaType(MediaType.valueOf(type), CharsetUtil.CHARSET_UTF_8);
            return new DownloadTableContent(mediaType, is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据 UUID 拼接文件的本地存储路径。
     *
     * @param uuid 文件唯一标识
     * @return 本地文件路径
     */
    private @NotNull String getPath(String uuid) {
        return this.baseFolder + uuid;
    }
}
