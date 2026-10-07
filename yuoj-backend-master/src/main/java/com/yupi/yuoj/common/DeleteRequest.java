package com.yupi.yuoj.common;

import java.io.Serializable;
import lombok.Data;

/**
 * @author han
 */
@Data
public class DeleteRequest implements Serializable {

    private Long id;

    private static final long serialVersionUID = 1L;
}