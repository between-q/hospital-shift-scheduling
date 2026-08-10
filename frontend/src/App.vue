<template>
  <AuthParams v-if="!auth.token" @login-success="handleLoginSuccess" />
  <AppLayout
    v-else
    :user="user"
    :current-view="currentView"
    :ws-connected="wsConnected"
    :ws-status="wsStatus"
    :notifications="notifications"
    @logout="handleLogout"
    @navigate="navigate"
  >
    <template #dashboard>
      <DashboardView
        :departments="departments"
        :shifts="shifts"
        :pending-task-count="pendingTaskCount"
        :summary="summary"
        :my-shifts="myShifts"
        :calendar-title="calendarTitle"
        :calendar-days="calendarDays"
        v-model:filterDeptId="calendarFilterDeptId"
        :is-admin="isAdmin"
        :shift-requests="shiftRequests"
        @refresh="loadDashboard"
        @changeMonth="changeMonth"
        @openDay="openCalendarDay"
        @approve-request="approveShiftRequest"
        @reject-request="rejectShiftRequest"
        @open-leave-form="showLeaveForm = true"
        @open-swap-form="showSwapForm = true"
      />
    </template>

    <template #shifts>
      <ShiftsView
        :key="shiftsVersion"
        :shifts="shifts"
        :loading="loadingData"
        :departments="departments"
        :users="adminUsers"
        :assignee-distribution="summary.assigneeDistribution"
        :department-distribution="summary.departmentDistribution"
        :is-admin="isAdmin"
        :on-delete-shift="deleteShiftDetails"
        :on-edit-shift="updateShiftDetails"
        :on-create-shift="createShift"
        @refresh="loadShifts"
        @open-leave-form="showLeaveForm = true"
        @open-swap-form="showSwapForm = true"
      />
    </template>

    <template #agent>
      <AgentView
        :messages="chatMessages"
        :ws-connected="wsConnected"
        :ws-status="wsStatus"
        :loading="loadingAgent"
        @send="sendChat"
        @connect="connectWs"
        @refresh="loadChatHistory"
      />
    </template>

    <template #profile>
      <ProfileView
        :user="user"
        :last-login="lastLogin"
        :is-admin="isAdmin"
        :admin-users="adminUsers"
        :shifts="shifts"
        :loading="loading"
        :pie-data="departmentShiftPie"
        @navigate="navigate"
        @reset-password="resetUserPassword"
        @update-shift="updateShiftDetails"
        @update-roles="updateUserRoles"
      />
    </template>
  </AppLayout>

  <!-- Calendar Day Detail Modal -->
  <div v-if="selectedDay" class="modal-overlay" @click="closeDayDetail">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>{{ selectedDay.key }} 班次详情</h3>
        <button class="modal-close" @click="closeDayDetail">✕</button>
      </div>
      <div class="modal-body">
        <div v-if="selectedDayShifts.length > 0" class="day-shifts-list">
          <div v-for="shift in selectedDayShifts" :key="shift.id" class="day-shift-item">
            <div class="shift-time">
              {{ new Date(shift.startTime).toLocaleTimeString('zh-CN', {hour: '2-digit', minute:'2-digit'}) }} -
              {{ new Date(shift.endTime).toLocaleTimeString('zh-CN', {hour: '2-digit', minute:'2-digit'}) }}
            </div>
            <div class="shift-info">
              <span class="shift-dept">{{ shift.departmentName || '未分配科室' }}</span>
              <span class="shift-role">{{ shift.requiredRole }}</span>
              <span :class="['shift-status', `status-${shift.status}`]">
                {{ shift.status === 'ASSIGNED' ? '已指派' : shift.status === 'OPEN' ? '待指派' : shift.status }}
              </span>
            </div>
            <div v-if="shift.assigneeName" class="shift-assignee">
               {{ shift.assigneeName }}
            </div>
          </div>
        </div>
        <div v-else class="empty-state">
          当天暂无班次安排
        </div>

        <!-- Calendar entries for this day -->
        <div v-if="calendarData.calendarShifts[selectedDay.key] && calendarData.calendarShifts[selectedDay.key].length > 0" class="calendar-entries">
          <h4>值班安排</h4>
          <div v-for="(entry, idx) in calendarData.calendarShifts[selectedDay.key]" :key="idx" class="calendar-entry-item">
            {{ entry.label }}
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 休假申请弹窗 -->
  <LeaveRequestForm
    :show="showLeaveForm"
    :my-shifts="myShifts"
    :on-submit="submitShiftRequest"
    @close="showLeaveForm = false"
  />

  <!-- 换班申请弹窗 -->
  <SwapRequestForm
    :show="showSwapForm"
    :my-shifts="myShifts"
    :on-submit="submitShiftRequest"
    @close="showSwapForm = false"
  />
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useApi } from './composables/useApi';
import { useWebSocket } from './composables/useWebSocket';

import AuthParams from './components/AuthParams.vue';
import AppLayout from './components/AppLayout.vue';
import DashboardView from './components/DashboardView.vue';
import ShiftsView from './components/ShiftsView.vue';
import AgentView from './components/AgentView.vue';
import LeaveRequestForm from './components/LeaveRequestForm.vue';
import SwapRequestForm from './components/SwapRequestForm.vue';
import ProfileView from './components/ProfileView.vue';

const { api, token: authToken, API_BASE } = useApi();
const WS_BASE = import.meta.env.VITE_WS_BASE || 'http://localhost:9090/ws';

// State
const loading = ref(false);
const loadingData = ref(false);
const loadingAgent = ref(false);
const shiftsVersion = ref(0);

const auth = reactive({
  token: localStorage.getItem('jwt_token') || null
});

const { isConnected: wsConnected, status: wsStatus, connect: wsConnect, disconnect: wsDisconnect, sendMessage: wsSendMessage } = useWebSocket(WS_BASE, auth.token);

const user = reactive(JSON.parse(localStorage.getItem('user_info') || '{}'));
const currentView = ref('dashboard');
const lastLogin = ref(localStorage.getItem('last_login') || null);

const departments = ref([]);
const shifts = ref([]);
const agentTasks = ref([]);
const notifications = ref([]);
const summary = reactive({
  totalShifts: 0,
  nightShifts: 0,
  assignedShifts: 0,
  unassignedShifts: 0,
  totalAssignees: 0,
  roleDistribution: [],
  departmentDistribution: [],
  assigneeDistribution: []
});

const calendarMonth = ref(new Date());
const calendarFilterDeptId = ref('');
const calendarData = reactive({
  year: 0,
  month: 0,
  calendarShifts: {}
});
const myShiftItems = ref([]);
const chatMessages = ref([]);
const adminUsers = ref([]);
const shiftRequests = ref([]);
const showLeaveForm = ref(false);
const showSwapForm = ref(false);

// Computed
const isAdmin = computed(() => Array.isArray(user.roles) && user.roles.includes('ADMIN'));
const pendingTaskCount = computed(() => agentTasks.value.filter(task => ['PENDING', 'IN_PROGRESS'].includes(task.status)).length);

const myShifts = computed(() => {
  // If we have data from overviewPanel, use it
  if (myShiftItems.value && myShiftItems.value.length > 0) {
    return myShiftItems.value;
  }
  // Otherwise, filter shifts by current user ID
  const userId = user.id;
  if (!userId || !shifts.value || shifts.value.length === 0) {
    return [];
  }
  return shifts.value.filter(s => s.assigneeUserId === userId);
});

const calendarTitle = computed(() => {
  const date = calendarMonth.value;
  return `${date.getFullYear()}年${date.getMonth() + 1}月`;
});

const calendarDays = computed(() => {
  const year = calendarMonth.value.getFullYear();
  const month = calendarMonth.value.getMonth();
  const firstDay = new Date(year, month, 1);
  const lastDay = new Date(year, month + 1, 0);
  const startDayOfWeek = firstDay.getDay();
  const daysInMonth = lastDay.getDate();

  const cells = [];
  const today = new Date();

  // Previous month padding
  const prevMonthLastDay = new Date(year, month, 0).getDate();
  for (let i = startDayOfWeek - 1; i >= 0; i--) {
    const day = prevMonthLastDay - i;
    const date = new Date(year, month - 1, day);
    cells.push({
      key: formatDateKey(date),
      dayNumber: day,
      inMonth: false,
      items: []
    });
  }

  // Current month
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(year, month, day);
    const dateKey = formatDateKey(date);
    const items = calendarData.calendarShifts[dateKey] || [];
    cells.push({
      key: dateKey,
      dayNumber: day,
      inMonth: true,
      items: items
    });
  }

  // Next month padding
  const remainingCells = 42 - cells.length;
  for (let day = 1; day <= remainingCells; day++) {
    const date = new Date(year, month + 1, day);
    cells.push({
      key: formatDateKey(date),
      dayNumber: day,
      inMonth: false,
      items: []
    });
  }

  return cells;
});

const pad2 = (val) => String(val).padStart(2, '0');

const formatDateKey = (date) => `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

const formatMonthParam = (input) => {
  const date = input instanceof Date ? input : new Date(input);
  if (Number.isNaN(date.getTime())) {
    const now = new Date();
    return `${now.getFullYear()}-${pad2(now.getMonth() + 1)}`;
  }
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}`;
};

const formatDateParam = (input) => {
  const date = input instanceof Date ? input : new Date(input);
  if (Number.isNaN(date.getTime())) {
    return formatDateKey(new Date());
  }
  return formatDateKey(date);
};

const normalizeChatText = (value) => {
  const text = (value ?? '').toString().replace(/\r\n/g, '\n').trim();
  if (!text) return '';

  return text;
};

const normalizeMessageRole = (msg = {}) => {
  const role = String(msg.role || msg.sender || '').toUpperCase();
  if (role === 'AGENT' || role === 'ASSISTANT' || role === 'BOT') return 'AGENT';
  if (role === 'SYSTEM') return 'SYSTEM';
  return 'USER';
};

// Actions
const handleLoginSuccess = (res) => {
  auth.token = res.token;
  authToken.value = res.token; // Update useApi token state
  localStorage.setItem('jwt_token', res.token);
  Object.assign(user, {
    id: res.userId,
    email: res.email,
    fullName: res.fullName,
    roles: res.roles
  });
  localStorage.setItem('user_info', JSON.stringify(user));
  lastLogin.value = new Date().toISOString();
  navigate('dashboard');

  // Re-initialize WebSocket connection with new token
  connectWs();
};

const handleLogout = () => {
  auth.token = null;
  authToken.value = null; // Clear useApi token state
  localStorage.removeItem('jwt_token');
  localStorage.removeItem('user_info');
  disconnectWs();
};

const navigate = (view) => {
  currentView.value = view;
  if (view === 'dashboard') loadDashboard();
  if (view === 'shifts') {
    loadShifts();
    loadAdminUsers();
  }
  if (view === 'agent') {
    loadChatHistory();
  }
  if (view === 'profile' && isAdmin.value) {
    loadAdminUsers();
    loadShifts(); // Update shift stats for admin
  }
};

const changeMonth = (offset) => {
  const cur = calendarMonth.value;
  calendarMonth.value = new Date(cur.getFullYear(), cur.getMonth() + offset, 1);
  loadDashboard();
};

const selectedDay = ref(null);
const selectedDayShifts = ref([]);

const openCalendarDay = (cell) => {
  if (!cell.inMonth) return;

  selectedDay.value = cell;
  // Filter shifts for this day
  const dayShifts = shifts.value.filter(s => {
    if (!s.startTime) return false;
    const shiftDate = new Date(s.startTime).toISOString().split('T')[0];
    return shiftDate === cell.key;
  });
  selectedDayShifts.value = dayShifts;
};

const closeDayDetail = () => {
  selectedDay.value = null;
  selectedDayShifts.value = [];
};

// API Loaders
const updateSummary = (shiftList) => {
  summary.totalShifts = shiftList.length;
  summary.unassignedShifts = shiftList.filter(s => !s.assigneeUserId).length;
  summary.assignedShifts = shiftList.filter(s => !!s.assigneeUserId).length;
  summary.nightShifts = shiftList.filter(s => s.shiftType === 'NIGHT').length;

  const deptMap = new Map();
  const assigneeMap = new Map();
  const roleMap = new Map();

  shiftList.forEach(s => {
    const dept = s.departmentName || s.departmentId || '未分配';
    deptMap.set(dept, (deptMap.get(dept) || 0) + 1);

    if (s.assigneeUserId) {
      const assignee = s.assigneeName || `User ${s.assigneeUserId}`;
      assigneeMap.set(assignee, (assigneeMap.get(assignee) || 0) + 1);
    }

    if (s.requiredRole) {
      roleMap.set(s.requiredRole, (roleMap.get(s.requiredRole) || 0) + 1);
    }
  });

  summary.departmentDistribution = Array.from(deptMap.entries())
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value);

  summary.assigneeDistribution = Array.from(assigneeMap.entries())
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 10);

  // Also populating roleDistribution just in case
  summary.roleDistribution = Array.from(roleMap.entries())
     .map(([label, value]) => ({ label, value }));
};

const applyShiftManagement = (shiftManagement, options = {}) => {
  if (!shiftManagement) return;

  const { preserveShifts = false } = options;
  const shiftList = Array.isArray(shiftManagement.shifts)
    ? shiftManagement.shifts
    : Array.isArray(shiftManagement?.data?.shifts)
      ? shiftManagement.data.shifts
      : [];

  if (!preserveShifts && shiftList.length > 0) {
    shifts.value = shiftList;
  }

  const baseShiftList = Array.isArray(shifts.value) && shifts.value.length > 0 ? shifts.value : shiftList;
  const computedTotal = baseShiftList.length;
  const computedAssigned = baseShiftList.filter((s) => s?.assigneeUserId != null).length;
  const computedPending = Math.max(computedTotal - computedAssigned, 0);
  const computedNight = baseShiftList.filter((s) => {
    const t = String(s?.startTime || '');
    return t.includes('T16:') || t.includes('T17:') || t.includes('T18:') || t.includes('T19:') ||
      t.includes('T20:') || t.includes('T21:') || t.includes('T22:') || t.includes('T23:');
  }).length;

  const stats = shiftManagement.stats || shiftManagement?.data?.stats || {};
  const statsTotal = Number(stats.totalShifts ?? NaN);
  const statsAssigned = Number(stats.assignedShifts ?? NaN);
  const statsPending = Number(stats.pendingShifts ?? stats.unassignedShifts ?? NaN);
  const statsNight = Number(stats.nightShifts ?? NaN);

  const preferComputed = computedTotal > 0 && (!Number.isFinite(statsTotal) || statsTotal === 0);

  summary.totalShifts = preferComputed ? computedTotal : (Number.isFinite(statsTotal) ? statsTotal : computedTotal);
  summary.assignedShifts = preferComputed ? computedAssigned : (Number.isFinite(statsAssigned) ? statsAssigned : computedAssigned);
  summary.unassignedShifts = preferComputed ? computedPending : (Number.isFinite(statsPending) ? statsPending : computedPending);
  summary.nightShifts = preferComputed ? computedNight : (Number.isFinite(statsNight) ? statsNight : computedNight);

  const charts = shiftManagement.charts || shiftManagement?.data?.charts || {};
  summary.assigneeDistribution = Array.isArray(charts.staffDistribution) ? charts.staffDistribution : [];
  summary.departmentDistribution = Array.isArray(charts.departmentDistribution) ? charts.departmentDistribution : [];
};

const applyOverviewPanel = (overviewPanel) => {
  if (!overviewPanel) return;
  myShiftItems.value = overviewPanel.myShifts || [];
  const calendar = overviewPanel.calendar || {};
  calendarData.year = calendar.year || calendarData.year;
  calendarData.month = calendar.month || calendarData.month;
  calendarData.calendarShifts = calendar.calendarShifts || {};
};

const DEMO_MONTH_FALLBACK = '2026-03';

const loadShifts = async () => {
  loadingData.value = true;
  try {
    const realtimeShifts = await api(`/shifts?_t=${Date.now()}`).catch(() => []);
    shifts.value = Array.isArray(realtimeShifts) ? realtimeShifts : [];
    updateSummary(shifts.value);

    const monthParam = formatMonthParam(calendarMonth.value || new Date());
    try {
      const visualization = await api(`/analytics/visualization?month=${monthParam}`);
      if (visualization) {
        applyShiftManagement(visualization.shiftManagement || visualization, { preserveShifts: true });
      }
    } catch (_) {
      // visualization is optional
    }
  } finally {
    loadingData.value = false;
  }
};

const loadDashboard = async () => {
  loadingData.value = true;
  try {
    const monthParam = formatMonthParam(calendarMonth.value || new Date()) || DEMO_MONTH_FALLBACK;
    const year = calendarMonth.value.getFullYear();
    const month = calendarMonth.value.getMonth() + 1;
    const startDate = `${year}-${pad2(month)}-01`;
    const endDate = `${year}-${pad2(month)}-${pad2(new Date(year, month, 0).getDate())}`;

    const [deptRes, taskRes, shiftRes, calRes] = await Promise.all([
      api('/departments').catch(() => []),
      api('/agent/tasks/pending').catch(() => []),
      api('/shifts').catch(() => []),
      api(`/calendar?start=${startDate}&end=${endDate}`).catch(() => [])
    ]);

    departments.value = Array.isArray(deptRes) ? deptRes : [];
    agentTasks.value = Array.isArray(taskRes) ? taskRes : [];
    shifts.value = Array.isArray(shiftRes) ? shiftRes : [];
    updateSummary(shifts.value);

    // 加载申请列表（管理员）
    if (isAdmin.value) {
      loadShiftRequests();
    }

    // Populate calendar from duty calendar entries
    const calendarShifts = {};
    if (Array.isArray(calRes)) {
      calRes.forEach(entry => {
        if (entry.date) {
          const label = entry.summary || `值班 (${entry.headcount || 0}人)`;
          if (!calendarShifts[entry.date]) {
            calendarShifts[entry.date] = [];
          }
          calendarShifts[entry.date].push({ label, dept: entry.departmentName || '' });
        }
      });
    }
    calendarData.calendarShifts = calendarShifts;
    calendarData.year = year;
    calendarData.month = month;

    try {
      const visualization = await api(`/analytics/visualization?month=${monthParam}`);
      if (visualization?.shiftManagement || visualization?.overviewPanel) {
        applyShiftManagement(visualization.shiftManagement || visualization, { preserveShifts: true });
        applyOverviewPanel(visualization.overviewPanel || {});
      }
    } catch (_) {
      // visualization is optional, fallback below
    }
  } finally {
    loadingData.value = false;
  }
};

const loadChatHistory = async () => {
  loadingAgent.value = true;
  try {
    const res = await api('/agent/chat?limit=50').catch(() => []);
    const list = Array.isArray(res) ? res : (Array.isArray(res?.items) ? res.items : []);
    chatMessages.value = list.map((m, idx) => ({
      id: m.id ?? `msg-${idx}`,
      role: normalizeMessageRole(m),
      sender: m.sender || m.fullName || m.username || (normalizeMessageRole(m) === 'AGENT' ? '智能体' : '用户'),
      timestamp: m.timestamp || m.createdAt || null,
      content: normalizeChatText(m.content || m.message || '')
    })).filter((m) => m.content);
  } finally {
    loadingAgent.value = false;
  }
};

const sendChat = async (payload) => {
  const text = normalizeChatText(payload?.message || payload || '');
  if (!text) return;

  chatMessages.value.push({
    id: `local-user-${Date.now()}`,
    role: 'USER',
    sender: user.fullName || user.email || '用户',
    timestamp: new Date().toISOString(),
    content: text
  });

  loadingAgent.value = true;
  try {
    const res = await api('/agent/coze-chat', {
      method: 'POST',
      body: JSON.stringify({ message: text, content: text }),
      headers: { 'Content-Type': 'application/json' }
    });

    const reply = normalizeChatText([
      res?.reply,
      res?.content,
      res?.message,
      res?.response,
      res?.data?.reply,
      res?.data?.content,
      res?.data?.message,
      res?.data?.response
    ].find(v => typeof v === 'string' && v.trim().length > 0) || '已发送，暂无回复内容');

    chatMessages.value.push({
      id: `local-bot-${Date.now()}`,
      role: 'AGENT',
      sender: '智能体',
      timestamp: new Date().toISOString(),
      content: reply
    });
  } catch (e) {
    chatMessages.value.push({
      id: `local-err-${Date.now()}`,
      role: 'SYSTEM',
      sender: '系统',
      timestamp: new Date().toISOString(),
      content: normalizeChatText(`发送失败: ${e?.message || '未知错误'}`)
    });
  } finally {
    loadingAgent.value = false;
  }
};

const loadAdminUsers = async () => {
  if (!isAdmin.value) return;
  try {
    const res = await api('/admin/users').catch(() => []);
    adminUsers.value = Array.isArray(res) ? res : (Array.isArray(res?.items) ? res.items : []);
  } catch (_) {
    adminUsers.value = [];
  }
};

const resetUserPassword = async (payload) => {
  if (!isAdmin.value || !payload) return;
  await api('/admin/users/reset-password', {
    method: 'POST',
    body: JSON.stringify(payload),
    headers: { 'Content-Type': 'application/json' }
  });
  await loadAdminUsers();
};

const updateUserRoles = async (payload) => {
  if (!isAdmin.value || !payload) return;
  await api(`/admin/users/${payload.userId}/roles`, {
    method: 'PUT',
    body: JSON.stringify(payload.roles),
    headers: { 'Content-Type': 'application/json' }
  });
  await loadAdminUsers();
};

const normalizeShiftPayload = (payload) => ({
  id: payload.id,
  departmentId: payload.departmentId ?? null,
  assigneeUserId: payload.assigneeUserId ?? null,
  requiredRole: payload.requiredRole ?? null,
  shiftType: payload.shiftType ?? null,
  status: payload.status ?? null,
  startTime: payload.startTime ?? null,
  endTime: payload.endTime ?? null,
  notes: payload.notes ?? null
});

const updateShiftDetails = async (payload) => {
  if (!payload?.id) {
    throw new Error('排班ID缺失');
  }

  const body = normalizeShiftPayload(payload);
  const updatedShift = await api(`/admin/shifts/${payload.id}`, {
    method: 'PUT',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' }
  });

  // 直接更新本地数据
  const idx = shifts.value.findIndex(s => s.id === payload.id);
  if (idx !== -1 && updatedShift) {
    const newArr = [...shifts.value];
    newArr[idx] = { ...newArr[idx], ...updatedShift };
    shifts.value = newArr;
    shiftsVersion.value++;
  }
  await loadDashboard();
};

const deleteShiftDetails = async (shiftId) => {
  if (!shiftId) throw new Error('排班ID缺失');
  await api(`/shifts/${shiftId}`, { method: 'DELETE' });
  console.log('删除排班成功:', shiftId);
  shifts.value = shifts.value.filter(s => s.id !== shiftId);
  shiftsVersion.value++;
  await Promise.all([loadDashboard()]);
};

const createShift = async (payload) => {
  const body = {
    startTime: payload.startTime,
    endTime: payload.endTime,
    requiredRole: payload.requiredRole,
    departmentId: payload.departmentId,
    notes: payload.notes || null
  };
  console.log('创建班次:', body);
  const newShift = await api('/shifts', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' }
  });
  console.log('创建成功:', newShift);
  if (newShift) {
    shifts.value = [...shifts.value, newShift];
    shiftsVersion.value++;
  }
};

const loadShiftRequests = async () => {
  try {
    const data = await api('/shift-requests');
    shiftRequests.value = Array.isArray(data) ? data : [];
  } catch (e) {
    console.error('加载申请列表失败:', e);
  }
};

const submitShiftRequest = async (payload) => {
  await api('/shift-requests', {
    method: 'POST',
    body: JSON.stringify(payload),
    headers: { 'Content-Type': 'application/json' }
  });
  await loadShiftRequests();
};

const approveShiftRequest = async (id) => {
  await api(`/shift-requests/${id}/approve`, { method: 'PUT' });
  await loadShiftRequests();
  await loadShifts();
};

const rejectShiftRequest = async (id) => {
  await api(`/shift-requests/${id}/reject`, { method: 'PUT' });
  await loadShiftRequests();
};

const connectWs = () => {
  try {
    wsConnect();
  } catch (_) {
    // no-op
  }
};

const disconnectWs = () => {
  try {
    wsDisconnect();
  } catch (_) {
    // no-op
  }
};

onMounted(async () => {
  if (!auth.token) return;
  authToken.value = auth.token;
  connectWs();
  await loadDashboard();
});
</script>

<style>
/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: modalSlideIn 0.3s ease;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e0ff;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #2c2c2c;
}

.modal-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #999;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}

.modal-close:hover {
  background: #f5f3ff;
  color: #6366f1;
}

.modal-body {
  padding: 24px;
}

.day-shifts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.day-shift-item {
  padding: 16px;
  background: #f9f7ff;
  border-radius: 8px;
  border-left: 4px solid #6366f1;
}

.shift-time {
  font-size: 14px;
  font-weight: 600;
  color: #6366f1;
  margin-bottom: 8px;
}

.shift-info {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.shift-dept {
  font-size: 13px;
  color: #2c2c2c;
  font-weight: 500;
}

.shift-role {
  font-size: 12px;
  padding: 2px 8px;
  background: #ede9fe;
  color: #6366f1;
  border-radius: 4px;
}

.shift-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.shift-status.status-ASSIGNED {
  background: #dcfce7;
  color: #15803d;
}

.shift-status.status-OPEN {
  background: #fef3c7;
  color: #92400e;
}

.shift-assignee {
  margin-top: 8px;
  font-size: 13px;
  color: #666;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #999;
  font-size: 14px;
}

.calendar-entries {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e5e0ff;
}

.calendar-entries h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  font-weight: 600;
  color: #2c2c2c;
}

.calendar-entry-item {
  padding: 8px 12px;
  background: #f0ebff;
  border-radius: 6px;
  font-size: 13px;
  color: #6366f1;
  margin-bottom: 8px;
}
</style>