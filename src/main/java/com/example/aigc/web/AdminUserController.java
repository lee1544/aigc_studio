package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.dto.contract.AdminUserListItemDto;
import com.example.aigc.dto.contract.ContractUserDto;
import com.example.aigc.dto.contract.CreateUserContractRequest;
import com.example.aigc.dto.contract.UpdateUserContractRequest;
import com.example.aigc.dto.contract.UpdateUserStatusRequest;
import com.example.aigc.dto.contract.UserMapper;
import com.example.aigc.entity.User;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.service.AdminUserListService;
import com.example.aigc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理员对创作者账号的管理：/api/admin/users。
 * 列表为分页 + 关键字搜索 + 状态筛选；创建/修改/状态/软删除返回字段按契约收紧（无 password/permissions）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AdminUserListService adminUserListService;

    /**
     * 创作者列表。page 从 0 开始；pageSize 有效范围 1-100，越界或非正数按 20。
     * keyword 模糊匹配 name/realName/email/username/phone；status=active|disabled|deleted|all，缺省排除 deleted。
     */
    @RequirePermission("user:view")
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status) {
        Page<AdminUserListItemDto> p = adminUserListService.page(page, pageSize, keyword, status);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", p.getContent());
        m.put("total", p.getTotalElements());
        m.put("page", p.getNumber());
        m.put("pageSize", p.getSize());
        return m;
    }

    @RequirePermission("user:create")
    @PostMapping
    public ContractUserDto create(@Valid @RequestBody CreateUserContractRequest req) {
        User u = userService.createCreator(req);
        return UserMapper.toListItem(u);
    }

    @RequirePermission("user:update")
    @PatchMapping("/{id}")
    public ContractUserDto patch(@PathVariable Integer id,
                                 @Valid @RequestBody UpdateUserContractRequest req) {
        User u = userService.patchCreator(id, req);
        return UserMapper.toListItem(u);
    }

    @RequirePermission("user:delete")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        userService.softDeleteCreator(id);
    }

    @RequirePermission("user:update")
    @PatchMapping("/{id}/status")
    public ContractUserDto patchStatus(@PathVariable Integer id,
                                       @Valid @RequestBody UpdateUserStatusRequest req) {
        // body.id 可选；若传入且与路径 id 不同则 400，不静默忽略
        if (req.getId() != null && !req.getId().equals(id)) {
            throw new BusinessException(400, "body.id 与路径 id 不一致");
        }
        User u = userService.patchCreatorStatus(id, req.getStatus());
        return UserMapper.toListItem(u);
    }
}
