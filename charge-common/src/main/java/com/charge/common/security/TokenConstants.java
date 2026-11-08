package com.charge.common.security;

/**
 * Token 相关常量
 */
public interface TokenConstants {

    /** 请求头名称 */
    String HEADER = "Authorization";

    /** Bearer 前缀 */
    String BEARER = "Bearer ";

    /** JWT 声明：租户 id */
    String CLAIM_TENANT = "tid";

    /** JWT 声明：角色 */
    String CLAIM_ROLE = "role";

    /** JWT 声明：token 类型（user / service） */
    String CLAIM_TYPE = "typ";

    /** token 类型：普通用户 */
    String TYPE_USER = "user";

    /** token 类型：服务间调用 */
    String TYPE_SERVICE = "service";
}
