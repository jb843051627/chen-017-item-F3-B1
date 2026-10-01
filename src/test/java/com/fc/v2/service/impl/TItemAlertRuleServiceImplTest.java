package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TItemAlertRuleMapper;
import com.fc.v2.model.auto.TItemAlertRule;

/**
 * 判定线单测：让线顺位全类只此一份——数值大的先说话，
 * 库里摆回来的先后不作数。
 *
 * @author fuce
 * @date 2026-10-01
 */
@ExtendWith(MockitoExtension.class)
class TItemAlertRuleServiceImplTest {

    @Mock
    private TItemAlertRuleMapper itemAlertRuleMapper;

    @InjectMocks
    private TItemAlertRuleServiceImpl service;

    @Test
    void evaluateTop_highestPrioritySpeaksFirst() {
        TItemAlertRule loose = rule("LINE-LOOSE", 1, "90.00", "95.00", "99.00");
        TItemAlertRule strict = rule("LINE-STRICT", 9, "10.00", "20.00", "30.00");
        // 库里松线摆在前头：顺位不看摆放先后。
        when(itemAlertRuleMapper.selectList(any())).thenReturn(Arrays.asList(loose, strict));

        // 输入 50：松线判 1 档，严线判 4 档；让线顺位数值大的严线先说话。
        assertEquals(4, service.evaluateTop(new BigDecimal("50"), new Date()));
    }

    private TItemAlertRule rule(String code, int priority, String th1, String th2, String th3) {
        TItemAlertRule r = new TItemAlertRule();
        r.setRuleCode(code);
        r.setPriority(priority);
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        return r;
    }
}
