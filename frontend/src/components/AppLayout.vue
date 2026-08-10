<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-icon"></div>
        <div>
          <div class="brand-title">排班系统</div>
          <div class="text-muted text-sm">Hospital Agent</div>
        </div>
      </div>

      <nav class="mt-4">
        <div class="nav-item" :class="{ active: currentView === 'dashboard' }" @click="navigate('dashboard')">
          📊 概览面板
        </div>
        <div class="nav-item" :class="{ active: currentView === 'shifts' }" @click="navigate('shifts')">
           排班管理
        </div>
        <div class="nav-item" :class="{ active: currentView === 'agent' }" @click="navigate('agent')">
           智能排班
        </div>
        <div class="nav-item" :class="{ active: currentView === 'profile' }" @click="navigate('profile')">
          🧑‍️ 个人中心
        </div>
      </nav>

      <div class="profile-card">
        <div class="avatar">{{ userInitials }}</div>
        <div>
          <div class="profile-name">{{ user.fullName || '未命名用户' }}</div>
          <div class="text-muted text-sm">{{ user.roles ? user.roles.join(', ') : '' }}</div>
        </div>
      </div>
      <div class="connection-badge" :class="{ online: wsConnected }">
        <span class="dot"></span>
        {{ wsStatus }}
      </div>
      <button class="secondary" @click="logout">退出登录</button>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div>
          <div class="page-title">{{ viewTitle }}</div>
          <div class="text-muted text-sm">欢迎回来，{{ user.fullName || '用户' }}</div>
        </div>
        <div class="topbar-actions">
          <button class="ghost" @click="navigate('dashboard')">概览</button>
          <button class="ghost" @click="navigate('shifts')">班次</button>
          <button class="ghost" @click="navigate('agent')">智能体</button>
          <button class="ghost" @click="navigate('profile')">个人中心</button>
        </div>
      </header>

      <div v-if="notifications.length" class="notice-stack">
        <div v-for="note in notifications" :key="note.id" class="notice-pill">
          <span>{{ note.message }}</span>
          <span class="text-muted text-sm">{{ note.time }}</span>
        </div>
      </div>

      <div v-if="currentView === 'dashboard'" class="page">
        <slot name="dashboard"></slot>
      </div>
      <div v-if="currentView === 'shifts'" class="page">
        <slot name="shifts"></slot>
      </div>
      <div v-if="currentView === 'agent'" class="page">
        <slot name="agent"></slot>
      </div>
      <div v-if="currentView === 'profile'" class="page">
        <slot name="profile"></slot>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  user: Object,
  currentView: String,
  wsConnected: Boolean,
  wsStatus: String,
  notifications: Array
});

const emit = defineEmits(['logout', 'navigate']);

const viewTitle = computed(() => {
  if (props.currentView === 'dashboard') return '概览面板';
  if (props.currentView === 'shifts') return '班次管理';
  if (props.currentView === 'agent') return '智能体中心';
  if (props.currentView === 'profile') return '个人中心';
  return '排班系统';
});

const userInitials = computed(() => {
  const name = (props.user.fullName || props.user.email || 'U').trim();
  if (!name) return 'U';
  if (name.length <= 2) return name.toUpperCase();
  return name.slice(0, 2).toUpperCase();
});

const navigate = (view) => {
  emit('navigate', view);
};

const logout = () => {
  emit('logout');
};
</script>

<style scoped>
.app-layout {
  display: grid;
  grid-template-columns: 250px 1fr;
  min-height: 100vh;
}

.sidebar {
  background: rgba(255, 255, 255, 0.82);
  border-right: 1px solid var(--glass-border, #e6e2f4);
  padding: 1.25rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  box-shadow: 6px 0 18px rgba(79, 70, 229, 0.05);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  overflow-y: auto;
}

.sidebar-brand {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--border, #e6e2f4);
}

.brand-icon {
  font-size: 32px;
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-title {
  font-size: 16px;
  font-weight: 700;
  color: #2c2c2c;
}

.text-muted {
  color: #999;
}

.text-sm {
  font-size: 12px;
}

nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0;
}

.nav-item {
  padding: 0.75rem 1rem;
  cursor: pointer;
  border-radius: 0.8rem;
  margin-bottom: 0.25rem;
  color: var(--text-muted, #6b7280);
  background: transparent;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.nav-item:hover {
  background: #f0ecff;
  color: #4c3fd1;
  border-color: #d9d4ff;
}

.nav-item.active {
  background: #f0ecff;
  color: #4c3fd1;
  border-color: #d9d4ff;
  font-weight: 600;
}

.profile-card {
  margin-top: auto;
  padding: 0.9rem;
  border-radius: 1rem;
  background: linear-gradient(135deg, #f3f0ff 0%, #ffffff 100%);
  display: flex;
  gap: 0.75rem;
  align-items: center;
  border: 1px solid #e7e1ff;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 14px;
}

.profile-name {
  font-weight: 600;
  font-size: 13px;
  color: #2c2c2c;
}

.connection-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  background: #f0f0f0;
  font-size: 11px;
  color: #666;
}

.connection-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ccc;
}

.connection-badge.online {
  background: #dcfce7;
  color: #15803d;
}

.connection-badge.online .dot {
  background: #22c55e;
}

.secondary {
  padding: 8px 16px;
  border: 1px solid #e5e0ff;
  border-radius: 6px;
  background: white;
  color: #6366f1;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  width: 100%;
  transition: all 0.2s ease;
}

.secondary:hover {
  background: #f9f7ff;
  border-color: #6366f1;
}

.main-content {
  padding: 2rem;
  overflow-y: auto;
  animation: page-enter 0.45s ease;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.main-content .page {
  max-width: 1200px;
  margin: 0 auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  background: var(--glass-bg, rgba(255, 255, 255, 0.82));
  padding: 1rem 1.25rem;
  border-radius: 1.1rem;
  border: 1px solid var(--glass-border, #e6e2f4);
  box-shadow: var(--glass-shadow, 0 18px 40px rgba(109, 94, 252, 0.16));
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}

.page-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #2c2c2c;
}

.topbar-actions {
  display: flex;
  gap: 0.5rem;
}

.ghost {
  padding: 8px 14px;
  border: none;
  background: transparent;
  color: #666;
  cursor: pointer;
  font-size: 12px;
  border-radius: 6px;
  transition: all 0.2s ease;
  font-weight: 500;
}

.ghost:hover {
  background: #f9f7ff;
  color: #6366f1;
}

.notice-stack {
  padding: 8px 24px;
  background: #fef3c7;
  border-bottom: 1px solid #fcd34d;
  display: flex;
  gap: 12px;
  overflow-x: auto;
  margin-bottom: 1rem;
  border-radius: 0.75rem;
}

.notice-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  padding: 8px 12px;
  border-radius: 20px;
  font-size: 12px;
  color: #92400e;
  white-space: nowrap;
  border: 1px solid #fcd34d;
}

.mt-4 {
  margin-top: 16px;
}

@keyframes page-enter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
