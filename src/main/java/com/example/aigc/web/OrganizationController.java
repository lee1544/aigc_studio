package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.annotation.SuperAdminOnly;
import com.example.aigc.dto.CreateOrganizationRequest;
import com.example.aigc.dto.PageResponse;
import com.example.aigc.dto.UpdateOrganizationRequest;
import com.example.aigc.entity.Organization;
import com.example.aigc.exception.ApiResponse;
import com.example.aigc.service.OrganizationService;
import com.example.aigc.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 组织管理：/api/organizations。
 * <p>超级管理员可查看全部组织并创建/修改/删除；组织管理员只能查看自己的组织。
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    
    @Autowired
    private OrganizationService organizationService;

    /** 创建组织仅超级管理员；同时生成组织管理员/普通管理员角色与初始管理员账号。 */
    @SuperAdminOnly("创建组织")
    @RequirePermission("org:create")
    @PostMapping
    public Organization createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.createOrganization(request, UserContextUtil.isSuperAdmin());
    }

    @RequirePermission("org:view")
    @GetMapping("/{id}")
    public Organization getOrganization(@PathVariable Integer id) {
        return organizationService.getOrganizationById(id, UserContextUtil.getVisibleOrgId());
    }

    @RequirePermission("org:view")
    @GetMapping
    public PageResponse<Organization> getOrganizations(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", required = false) String keyword) {
        Sort sort = Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Organization> orgPage = organizationService.getOrganizations(
                keyword, UserContextUtil.getVisibleOrgId(), pageRequest);

        return PageResponse.of(
                orgPage.getContent(),
                orgPage.getNumber(),
                orgPage.getSize(),
                orgPage.getTotalElements()
        );
    }

    @SuperAdminOnly("修改组织")
    @RequirePermission("org:update")
    @PutMapping("/{id}")
    public Organization updateOrganization(@PathVariable Integer id,
                                           @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateOrganization(id, request, UserContextUtil.isSuperAdmin());
    }

    @SuperAdminOnly("删除组织")
    @RequirePermission("org:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrganization(@PathVariable Integer id) {
        organizationService.deleteOrganization(id, UserContextUtil.isSuperAdmin());
        return new ApiResponse<>(200, "组织已成功删除");
    }
}
