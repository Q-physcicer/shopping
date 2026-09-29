package com.shop.user.controller;

import com.shop.common.context.UserContext;
import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.common.util.Result;
import com.shop.user.pojo.UserAddress;
import com.shop.user.service.impl.UserAddressServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址接口（购物端个人中心）。
 * 用户身份一律 UserContext（网关注入的 X-User-Id），禁止路径传 userId。
 */
@RestController
@RequestMapping("/user/address")
public class AddressController {

    @Autowired
    private UserAddressServiceImpl addressService;

    /** 我的地址列表 */
    @GetMapping
    public Result list() {
        return Result.success("success", addressService.listByUser(requireUserId()));
    }

    /**
     * 按 ID 查本人地址（P0-6：order 下单经 Feign 回查地址做快照，FeignIdentityInterceptor
     * 透传下单用户身份，此处按透传的 X-User-Id 校验归属——网关直调同样只能查到自己的）。
     */
    @GetMapping("/internal/{id}")
    public Result getById(@PathVariable Long id) {
        UserAddress a = addressService.getOwned(requireUserId(), id);
        return a != null ? Result.success("success", a) : Result.fail("地址不存在", null);
    }

    /** 当前登录用户的默认地址（P0-6：Agent 直购无 addressId 时兜底；无默认返回空 data） */
    @GetMapping("/internal/default")
    public Result getDefault() {
        UserAddress a = addressService.getDefault(requireUserId());
        return Result.success("success", a);
    }

    /** 新增地址 */
    @PostMapping
    public Result add(@RequestBody UserAddress address) {
        validate(address);
        return Result.success("地址已保存", addressService.save(requireUserId(), address));
    }

    /** 修改地址 */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody UserAddress address) {
        validate(address);
        return addressService.update(requireUserId(), id, address)
                ? Result.success("地址已更新") : Result.fail("地址不存在或不属于当前用户", null);
    }

    /** 设为默认地址 */
    @PutMapping("/default/{id}")
    public Result setDefault(@PathVariable Long id) {
        return addressService.setDefault(requireUserId(), id)
                ? Result.success("已设为默认地址") : Result.fail("地址不存在或不属于当前用户", null);
    }

    /** 删除地址 */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        return addressService.delete(requireUserId(), id)
                ? Result.success("地址已删除") : Result.fail("地址不存在或不属于当前用户", null);
    }

    private void validate(UserAddress address) {
        if (address == null || isBlank(address.getReceiverName()) || isBlank(address.getReceiverPhone())
                || isBlank(address.getDetailAddress())) {
            throw new XmException("收货人、手机号、详细地址必填");
        }
        // 长度对齐 DDL 列宽（超长裸 500 会让前端整页跳 /error，表单全丢）
        if (address.getReceiverName().length() > 50) {
            throw new XmException("收货人姓名过长（≤50 字）");
        }
        if (!address.getReceiverPhone().matches("\\d{6,20}")) {
            throw new XmException("手机号格式不正确");
        }
        if (address.getDetailAddress().length() > 200) {
            throw new XmException("详细地址过长（≤200 字）");
        }
        String[] regions = {address.getProvince(), address.getCity(), address.getDistrict()};
        for (String r : regions) {
            if (r != null && r.length() > 20) {
                throw new XmException("省/市/区过长（≤20 字）");
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private Integer requireUserId() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new XmException(ExceptionEnum.GET_USER_NOT_FOUND);
        }
        return uid.intValue();
    }
}
