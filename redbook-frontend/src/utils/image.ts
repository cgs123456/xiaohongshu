/**
 * 为阿里云 OSS 图片追加缩略图处理参数
 * @param url 原图地址
 * @param width 缩略图宽度（默认 400px）
 */
export function withThumb(url: string | undefined | null, width = 400): string {
    if (!url) return ''
    if (url.includes('aliyuncs.com')) {
        const sep = url.includes('?') ? '&' : '?'
        return `${url}${sep}x-oss-process=image/resize,w_${width}`
    }
    return url
}
