<template>
  <div class="dashboard-wrapper">
    <div class="dashboard-container">
      <!-- 标题区 -->
      <div class="section-header">
        <h2>科室与概览</h2>
        <button class="btn-refresh" @click="$emit('refresh')">
          <span>🔄</span> 刷新
        </button>
      </div>

      <!-- 统计卡片 - 3列 -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-label">科室数量</div>
          <div class="stat-value">{{ departments.length }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">班次数量</div>
          <div class="stat-value">{{ shifts.length }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">待处理任务</div>
          <div class="stat-value">{{ pendingTaskCount }}</div>
        </div>
      </div>

      <!-- 下方3列布局 -->
      <div class="content-grid">
        <!-- 中列：管理员显示信息 / 普通用户显示我的排班 -->
        <div class="card">
          <!-- 管理员：信息卡片 -->
          <template v-if="isAdmin">
            <div class="card-header">信息 <span v-if="shiftRequests.length" class="badge">{{ shiftRequests.length }}</span></div>
            <div class="card-body">
              <div v-if="shiftRequests.length > 0" class="request-list">
                <div v-for="req in shiftRequests" :key="req.id" class="request-item">
                  <div class="request-header">
                    <span class="request-type" :class="req.requestType === 'LEAVE' ? 'type-leave' : 'type-swap'">
                      {{ req.requestType === 'LEAVE' ? '请假' : '换班' }}
                    </span>
                    <span class="request-time">{{ formatReqTime(req.createdAt) }}</span>
                  </div>
                  <div class="request-body">
                    <div class="request-user">{{ req.requesterName }}</div>
                    <div class="request-detail">班次 #{{ req.shiftId }} {{ req.shiftTime }}</div>
                    <div v-if="req.targetShiftId" class="request-detail">→ 换至 #{{ req.targetShiftId }} {{ req.targetShiftTime }}</div>
                    <div v-if="req.reason" class="request-reason">理由：{{ req.reason }}</div>
                  </div>
                  <div class="request-actions">
                    <button class="btn-approve" @click="$emit('approve-request', req.id)">通过</button>
                    <button class="btn-reject" @click="$emit('reject-request', req.id)">拒绝</button>
                  </div>
                </div>
              </div>
              <div v-else class="empty-state">暂无待处理信息</div>
            </div>
          </template>

          <!-- 普通用户：我的排班 -->
          <template v-else>
            <div class="card-header">我的排班</div>
            <div class="card-body">
              <div v-if="myShifts.length > 0" class="shift-list">
                <div v-for="shift in myShifts.slice(0, 5)" :key="shift.id" class="shift-item" @click="openShiftDetail(shift)">
                  <div class="shift-dept">{{ shift.departmentName || '科室' }}</div>
                  <div class="shift-meta">
                    <div class="shift-role">{{ shift.requiredRole || '未指定角色' }}</div>
                    <span :class="['status-badge', `status-${shift.status || 'OPEN'}`]">
                      {{ statusLabel(shift.status) }}
                    </span>
                  </div>
                  <div class="shift-time">{{ formatTime(shift.startTime) }}</div>
                </div>
              </div>
              <div v-else class="empty-state">暂无指派班次</div>
              <div v-if="myShifts.length > 0" class="action-buttons">
                <button class="btn-apply-leave" @click="$emit('open-leave-form')">休假申请</button>
                <button class="btn-apply-swap" @click="$emit('open-swap-form')">换班申请</button>
              </div>
            </div>
          </template>
        </div>

        <!-- 班次详情弹窗 -->
        <div v-if="selectedShift" class="modal-overlay" @click="closeShiftDetail">
          <div class="modal-content" @click.stop>
            <div class="modal-header">
              <h3>班次详情</h3>
              <button class="modal-close" @click="closeShiftDetail">✕</button>
            </div>
            <div class="modal-body">
              <div class="detail-row">
                <span class="detail-label">科室</span>
                <span class="detail-value">{{ selectedShift.departmentName || '未分配' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">角色</span>
                <span class="detail-value">{{ selectedShift.requiredRole || '未指定' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">状态</span>
                <span :class="['status-badge', `status-${selectedShift.status || 'OPEN'}`]">
                  {{ statusLabel(selectedShift.status) }}
                </span>
              </div>
              <div class="detail-row">
                <span class="detail-label">开始时间</span>
                <span class="detail-value">{{ formatTime(selectedShift.startTime) }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">结束时间</span>
                <span class="detail-value">{{ formatTime(selectedShift.endTime) }}</span>
              </div>
              <div v-if="selectedShift.notes" class="detail-row">
                <span class="detail-label">备注</span>
                <span class="detail-value">{{ selectedShift.notes }}</span>
              </div>
              <div v-if="selectedShift.assigneeName" class="detail-row">
                <span class="detail-label">指派人员</span>
                <span class="detail-value">{{ selectedShift.assigneeName }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右列：值班日历 -->
        <div class="card calendar-card">
          <div class="calendar-header">
            <div class="calendar-title">值班日历</div>
            <div class="calendar-controls">
              <select :value="filterDeptId" @change="$emit('update:filterDeptId', $event.target.value)" class="dept-select">
                <option value="">全部科室</option>
                <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
              </select>
              <div class="month-buttons">
                <button class="month-btn" @click="$emit('changeMonth', -1)">上个月</button>
                <button class="month-btn" @click="$emit('changeMonth', 1)">下个月</button>
              </div>
            </div>
          </div>
          <div class="calendar-body">
            <div class="calendar-date-display">{{ calendarTitle }}</div>
            <div class="calendar-grid-header">
              <div class="weekday" v-for="day in ['日','一','二','三','四','五','六']" :key="day">{{ day }}</div>
            </div>
            <div class="calendar-grid">
              <div
                v-for="cell in calendarDays"
                :key="cell.key"
                class="calendar-cell"
                :class="{ 'is-out': !cell.inMonth, 'is-today': isToday(cell) }"
                @click="$emit('openDay', cell)"
              >
                <div class="calendar-date">{{ cell.dayNumber }}</div>
                <div class="calendar-items">
                  <span
                    v-for="(item, idx) in cell.items.slice(0, 2)"
                    :key="idx"
                    class="calendar-item"
                    :title="item.label"
                  >{{ item.label }}</span>
                  <span v-if="cell.items.length > 2" class="calendar-more">+{{ cell.items.length - 2 }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  departments: Array,
  shifts: Array,
  pendingTaskCount: Number,
  summary: Object,
  myShifts: Array,
  calendarTitle: String,
  calendarDays: Array,
  filterDeptId: [String, Number],
  isAdmin: { type: Boolean, default: false },
  shiftRequests: { type: Array, default: () => [] }
});

defineEmits(['refresh', 'update:filterDeptId', 'changeMonth', 'openDay', 'navigate', 'approve-request', 'reject-request', 'open-leave-form', 'open-swap-form']);

const topNavItems = ['dashboard', 'shifts', 'agent'];

const selectedShift = ref(null);

const openShiftDetail = (shift) => {
  selectedShift.value = shift;
};

const closeShiftDetail = () => {
  selectedShift.value = null;
};

const formatTime = (isoString) => {
  if (!isoString) return '-';
  const d = new Date(isoString);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

const statusLabel = (status) => {
  const map = {
    OPEN: '待指派',
    ASSIGNED: '已指派',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  };
  return map[status] || (status ? status.replace(/_/g, ' ') : '未知');
};

const isToday = (cell) => {
  const today = new Date();
  const d = new Date(cell.key);
  return d.getDate() === today.getDate() &&
         d.getMonth() === today.getMonth() &&
         d.getFullYear() === today.getFullYear();
};

const formatReqTime = (isoString) => {
  if (!isoString) return '';
  const d = new Date(isoString);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};
</script>

<style scoped>
.dashboard-wrapper {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f5f3ff 0%, #f0ebff 100%);
}

.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding: 0 28px 32px 28px;
  height: 100%;
  overflow-y: auto;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding: 0 4px;
}

.section-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #2c2c2c;
  margin: 0;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.btn-refresh {
  padding: 10px 18px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.btn-refresh:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4);
}

.btn-refresh:active {
  transform: translateY(0);
}

/* 统计卡片网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 8px;
}

.stat-card {
  background: white;
  padding: 28px 24px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(99, 102, 241, 0.1);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
}

.stat-card:hover {
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.15);
  transform: translateY(-4px);
  border-color: rgba(99, 102, 241, 0.3);
}

.stat-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 14px;
  display: block;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* 内容网格 */
.content-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(380px, 2fr);
  gap: 24px;
  flex: 1;
  grid-auto-rows: minmax(0, 1fr);
}

.card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(99, 102, 241, 0.1);
}

.card:hover {
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.15);
  transform: translateY(-4px);
}

.card-header {
  padding: 20px;
  font-size: 15px;
  font-weight: 700;
  color: #2c2c2c;
  border-bottom: 1px solid #f3f0ff;
  background: #fafafa;
}

.card-body {
  padding: 28px 20px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 排班列表 */
.shift-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.shift-item {
  padding: 12px;
  border-radius: 8px;
  background: #f9f7ff;
  font-size: 12px;
  border-left: 3px solid #6366f1;
  transition: all 0.2s ease;
}

.shift-item:hover {
  background: #f3f0ff;
  transform: translateX(4px);
}

.shift-dept {
  font-weight: 600;
  color: #2c2c2c;
  margin-bottom: 4px;
}

.shift-role {
  color: #666;
  font-size: 11px;
  margin-bottom: 4px;
}

.shift-time {
  color: #6366f1;
  font-size: 11px;
  font-weight: 500;
}

.shift-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.shift-meta .status-badge {
  text-transform: none;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 32px 12px;
  color: #aaa;
  font-size: 13px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 日历卡片 */
.calendar-card {
  grid-column: 2;
  grid-row: 1;
  display: flex;
  flex-direction: column;
  min-height: 520px;
}

.calendar-header {
  padding: 20px;
  border-bottom: 1px solid #f3f0ff;
  background: #fafafa;
}

.calendar-title {
  font-size: 15px;
  font-weight: 700;
  color: #2c2c2c;
  margin-bottom: 12px;
}

.calendar-controls {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.dept-select {
  flex: 1;
  min-width: 100px;
  padding: 8px 10px;
  border: 1px solid #e5e0ff;
  border-radius: 6px;
  font-size: 12px;
  background: white;
  color: #2c2c2c;
  cursor: pointer;
  transition: all 0.2s ease;
}

.dept-select:hover {
  border-color: #6366f1;
  background: #f9f7ff;
}

.dept-select:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.month-buttons {
  display: flex;
  gap: 6px;
}

.month-btn {
  padding: 6px 12px;
  border: 1px solid #e5e0ff;
  border-radius: 6px;
  background: white;
  color: #2c2c2c;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-weight: 500;
  white-space: nowrap;
}

.month-btn:hover {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  border-color: transparent;
  transform: translateY(-1px);
}

.calendar-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.calendar-date-display {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
  font-weight: 500;
}

.calendar-grid-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-bottom: 8px;
}

.weekday {
  text-align: center;
  font-size: 11px;
  font-weight: 700;
  color: #666;
  padding: 4px 0;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  flex: 1;
  min-height: 360px;
}

.calendar-cell {
  aspect-ratio: 1;
  border: 1px solid #e5e0ff;
  border-radius: 6px;
  padding: 4px;
  font-size: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: white;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
}

.calendar-cell:hover {
  border-color: #6366f1;
  background: #f9f7ff;
  transform: translateY(-1px);
}

.calendar-cell.is-out {
  opacity: 0.4;
  cursor: default;
  background: #fafafa;
}

.calendar-cell.is-out:hover {
  border-color: #e5e0ff;
  background: #fafafa;
  transform: none;
}

.calendar-cell.is-today {
  border: 2px solid #6366f1;
  font-weight: 700;
  background: #ede9fe;
}

.calendar-date {
  font-weight: 600;
  color: #2c2c2c;
  margin-bottom: 1px;
}

.calendar-items {
  display: flex;
  flex-direction: column;
  gap: 1px;
  width: 100%;
  align-items: center;
  justify-content: flex-end;
}

.calendar-item {
  font-size: 8px;
  background: #6366f1;
  color: white;
  padding: 1px 2px;
  border-radius: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  font-weight: 500;
}

.calendar-more {
  font-size: 8px;
  color: #6366f1;
  font-weight: 600;
}

@media (max-width: 1400px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .calendar-card {
    grid-column: auto;
    grid-row: auto;
  }
}

@media (max-width: 900px) {
  .stats-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .calendar-card {
    grid-column: auto;
    grid-row: auto;
  }

  .dashboard-container {
    gap: 20px;
    padding: 20px;
  }
}

/* Shift Item Clickable */
.shift-item {
  cursor: pointer;
  transition: all 0.2s ease;
}

.shift-item:hover {
  transform: translateX(4px);
  box-shadow: 0 4px 12px rgba(109, 94, 252, 0.15);
  border-color: #6d5efc;
}

/* 申请按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.btn-apply-leave, .btn-apply-swap {
  flex: 1;
  padding: 10px;
  color: white;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-apply-leave {
  background: linear-gradient(135deg, #10b981, #059669);
}
.btn-apply-leave:hover { box-shadow: 0 4px 12px rgba(16,185,129,0.3); transform: translateY(-1px); }
.btn-apply-swap {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}
.btn-apply-swap:hover { box-shadow: 0 4px 12px rgba(245,158,11,0.3); transform: translateY(-1px); }

/* 信息卡片 - 请求列表 */
.badge {
  background: #ef4444;
  color: white;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 10px;
  margin-left: 6px;
  font-weight: 600;
}
.request-list { display: flex; flex-direction: column; gap: 10px; }
.request-item {
  padding: 12px;
  border-radius: 10px;
  background: #f9f7ff;
  border-left: 3px solid #6366f1;
  font-size: 12px;
}
.request-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.request-type {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
}
.type-leave { background: #fef3c7; color: #92400e; }
.type-swap { background: #dbeafe; color: #1e40af; }
.request-time { color: #999; font-size: 11px; }
.request-user { font-weight: 600; color: #2c2c2c; margin-bottom: 2px; }
.request-detail { color: #666; }
.request-reason { color: #888; margin-top: 4px; font-style: italic; }
.request-actions { display: flex; gap: 8px; margin-top: 8px; }
.btn-approve {
  flex: 1;
  padding: 6px;
  background: #10b981;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-approve:hover { background: #059669; }
.btn-reject {
  flex: 1;
  padding: 6px;
  background: #f3f4f6;
  color: #dc2626;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-reject:hover { background: #fee2e2; }

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
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 450px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
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
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid #f3f0ff;
}

.detail-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.detail-label {
  font-size: 13px;
  color: #666;
  font-weight: 500;
}

.detail-value {
  font-size: 14px;
  color: #2c2c2c;
  font-weight: 600;
}
</style>
