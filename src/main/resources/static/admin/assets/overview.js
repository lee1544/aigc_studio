(function () {
  'use strict';

  var AIGCS = window.AIGCS;
  var AdminPage = window.AdminPage;

  function el(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    var node = el(id);
    if (node) {
      node.textContent = value;
    }
  }

  function greetingOf(date) {
    var hour = date.getHours();
    if (hour < 6) {
      return '凌晨好';
    }
    if (hour < 11) {
      return '早上好';
    }
    if (hour < 13) {
      return '中午好';
    }
    if (hour < 18) {
      return '下午好';
    }
    return '晚上好';
  }

  function formatCount(value) {
    if (value === null || value === undefined || value === '' || isNaN(Number(value))) {
      return '\u2014';
    }
    return String(Number(value)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  function renderRoleBadges(node, roles) {
    if (!node) {
      return;
    }
    node.textContent = '';
    if (!roles.length) {
      var empty = document.createElement('span');
      empty.className = 'muted';
      empty.textContent = '-';
      node.appendChild(empty);
      return;
    }
    roles.forEach(function (role) {
      var badge = document.createElement('span');
      badge.className = 'role-badge';
      badge.textContent = role;
      node.appendChild(badge);
    });
  }

  function fillProfile(user) {
    var displayName = user.realName || user.username || '-';
    var roles = Array.isArray(user.roles) ? user.roles : [];
    var permissions = Array.isArray(user.permissions) ? user.permissions : [];

    setText('greetingTitle', greetingOf(new Date()) + '，' + displayName);
    setText('greetingSummary', user.firstLogin
      ? '检测到首次登录，建议尽快把初始密码改为个人密码。'
      : (user.orgName ? '所属组织：' + user.orgName : '登录状态正常，可从下方快捷入口开始办理。'));

    setText('heroAvatar', AIGCS.initials(user));
    setText('heroName', displayName);
    setText('heroUsername', user.username ? '@' + user.username : '-');
    setText('heroState', user.firstLogin ? '首次登录' : '登录正常');

    var note = el('firstLoginNote');
    if (note) {
      note.hidden = !user.firstLogin;
    }

    setText('profileUsername', user.username || '-');
    setText('profileRealName', user.realName || '-');
    setText('profileOrg', user.orgName || '-');
    renderRoleBadges(el('profileRoles'), roles);

    var permissionRow = el('permissionRow');
    if (permissionRow) {
      permissionRow.hidden = permissions.length === 0;
    }
    setText('profilePermissions', permissions.length + ' 项');

    setText('profileCreatedAt', AIGCS.formatDateTime(user.createdAt));
  }

  function loadStat(id, path, pick) {
    var node = el(id);
    if (!node) {
      return;
    }
    node.setAttribute('aria-busy', 'true');
    AIGCS.api(path, { timeout: 8000 }).then(function (payload) {
      node.removeAttribute('aria-busy');
      var value = pick(payload);
      if (value === null || value === undefined) {
        return;
      }
      node.textContent = formatCount(value);
    }, function (error) {
      node.removeAttribute('aria-busy');
      node.classList.add('is-unavailable');
      node.setAttribute('title', error && error.status === 403
        ? '没有权限查看该指标'
        : ((error && error.message) || '指标加载失败'));
    });
  }

  AdminPage.init().then(function (user) {
    if (!user) {
      return;
    }
    fillProfile(user);

    if (AdminPage.can('user:view')) {
      loadStat('statUsers', '/api/admin/users?pageSize=1', function (payload) {
        return payload && payload.total;
      });
    }
    if (AdminPage.can('org:view')) {
      loadStat('statOrgs', '/api/organizations?page=0&size=1', function (payload) {
        return payload && payload.totalElements;
      });
    }
    if (AdminPage.can('role:view')) {
      loadStat('statRoles', '/api/roles', function (payload) {
        return Array.isArray(payload) ? payload.length : null;
      });
    }
    loadStat('statGenerations', '/api/admin/generation-summary', function (payload) {
      return payload && payload.total;
    });
  });
})();
