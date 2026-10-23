package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.dto.CreateOrganizationRequest;
import com.example.aigc.dto.PageResponse;
import com.example.aigc.dto.UpdateOrganizationRequest;
import com.example.aigc.entity.Organization;
import com.example.aigc.exception.ApiResponse;
import com.example.aigc.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    
    @Autowired
    private OrganizationService organizationService;

    @RequirePermission("org:create")
    @PostMapping
    public Organization createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.createOrganization(request);
    }

    @RequirePermission("org:view")
    @GetMapping("/{id}")
    public Organization getOrganization(@PathVariable Integer id) {
        return organizationService.getOrganizationById(id);
    }

    @RequirePermission("org:view")
    @GetMapping
    public PageResponse<Organization> getOrganizations(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "keyword", required = false) String keyword) {
        Sort sort = Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<Organization> orgPage = organizationService.getOrganizations(keyword, pageRequest);

        return PageResponse.of(
                orgPage.getContent(),
                orgPage.getNumber(),
                orgPage.getSize(),
                orgPage.getTotalElements()
        );
    }

    @RequirePermission("org:update")
    @PutMapping("/{id}")
    public Organization updateOrganization(@PathVariable Integer id,
                                           @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateOrganization(id, request);
    }

    @RequirePermission("org:delete")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrganization(@PathVariable Integer id) {
        organizationService.deleteOrganization(id);
        return new ApiResponse<>(200, "组织已成功删除");
    }
}
