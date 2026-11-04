package com.example.aigc.service;

import com.example.aigc.entity.Permission;
import com.example.aigc.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private PermissionService permissionService;

    @Test
    void backendPermissionListExcludesCreatorFeatures() {
        when(permissionRepository.findAllByOrderByPermissionCodeAsc()).thenReturn(Arrays.asList(
                permission("user:view"),
                permission("role:view"),
                permission("script"),
                permission("textToImage")
        ));

        List<String> codes = permissionService.getAllBackendPermissions().stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList("user:view", "role:view"), codes);
    }

    private Permission permission(String code) {
        Permission permission = new Permission();
        permission.setPermissionCode(code);
        return permission;
    }
}
