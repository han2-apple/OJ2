package com.yupi.yuoj.model.vo;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * @author han
 */
@Data
public class LoginUserVO implements Serializable {

    private Long id;

    private String userName;

    private String userAvatar;

    private String userProfile;

    private String userRole;

    private Date createTime;

    private Date updateTime;

    private static final long serialVersionUID = 1L;
}