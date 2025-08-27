package com.winworld.coursestools.dto.payment.payeer;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PayeerRetrieveDto {
    private String m_shop;
    private String m_operation_ps;
    private String m_operation_date;
    private String m_operation_pay_date;
    private String m_operation_id;
    private String m_orderid;
    private String m_amount;
    private String m_curr;
    private String m_desc;
    private String m_status;
    private String m_sign;
}
