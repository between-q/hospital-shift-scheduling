<template>
  <div class="shifts-page">
    <header class="header shifts-header">
      <div style="display: flex; gap: 12px; align-items: center;">
        <template v-if="isAdmin">
          <button class="btn-template" @click="showTemplateModal = true">
             使用模板
          </button>
        </template>
        <template v-else>
          <button class="btn-leave" @click="$emit('openLeaveForm')">
             休假申请
          </button>
          <button class="btn-swap" @click="$emit('openSwapForm')">
             换班申请
          </button>
        </template>
        <button class="btn-refresh" style="width: auto;" @click="$emit('refresh')">刷新</button>
      </div>
    </header>

    <!-- 模板选择弹窗 -->
    <TemplateSelector 
      :show="showTemplateModal" 
      :departments="departments"
      @close="showTemplateModal = false"
      @apply="applyTemplateBatch"
    />

    <div class="stats">
      <div class="stat-card">
        <div class="stat-title">总班次</div>
        <div class="stat-value">{{ filteredShifts.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">已指派</div>
        <div class="stat-value" style="color: #10b981;">{{ assignedCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">待指派</div>
        <div class="stat-value" style="color: #f59e0b;">{{ unassignedCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-title">夜班</div>
        <div class="stat-value" style="color: #6366f1;">{{ nightShiftCount }}</div>
      </div>
    </div>

    <div class="filter-row">
      <div class="filter-group">
        <label>科室</label>
        <select v-model="filterDeptId" class="filter-select">
          <option value="">全部科室</option>
          <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>状态</label>
        <select v-model="filterStatus" class="filter-select">
          <option value="">全部状态</option>
          <option v-for="status in statusOptions" :key="status" :value="status">{{ statusLabel(status) }}</option>
        </select>
      </div>
      <div class="filter-group">
        <label>班次</label>
        <select v-model="filterShiftType" class="filter-select">
          <option value="">全部班次</option>
          <option value="DAY">白班</option>
          <option value="NIGHT">夜班</option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="btn-outline" @click="resetFilters">重置筛选</button>
      </div>
    </div>

    <div class="shifts-layout">
      <div class="card table-card">
        <div class="table-wrap">
          <table class="shift-table table">
            <thead>
              <tr>
                <th>序号</th>
                <th>时间范围</th>
                <th>科室</th>
                <th>必需角色</th>
                <th>状态</th>
                <th v-if="isAdmin">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td :colspan="isAdmin ? 6 : 5" class="text-center">加载中...</td></tr>
              <tr v-else-if="filteredShifts.length === 0"><td :colspan="isAdmin ? 6 : 5" class="text-center">暂无排班</td></tr>
              <tr v-for="(shift, index) in filteredShifts" :key="shift.id">
                <td>{{ index + 1 }}</td>
                <td>
                  {{ formatTime(shift.startTime) }} <br />
                  <span class="text-muted text-sm">{{ formatTime(shift.endTime) }}</span>
                </td>
                <td>{{ shift.departmentName || shift.departmentId || '-' }}</td>
                <td><span class="chip">{{ shift.requiredRole || '未指定' }}</span></td>
                <td>
                  <span :class="['status-badge', `status-${shift.status || 'OPEN'}`]">{{ statusLabel(shift.status) }}</span>
                </td>
                <td v-if="isAdmin">
                  <button class="btn-edit" @click="openEditModal(shift)">编辑</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card distribution-card">
        <div class="panel-title">班次类型占比</div>
        <div class="pie-3d-wrapper">
          <div class="pie-3d-container">
            <div class="pie-3d" :style="pieChartStyle"></div>
            <div class="pie-3d-thickness" :style="pieChartThicknessStyle"></div>
          </div>
          <div class="pie-legend-list">
             <div class="legend-item">
               <span class="legend-dot" style="background: #a855f7;"></span>
               <span>白班 {{ dayShiftCount }}</span>
             </div>
             <div class="legend-item">
               <span class="legend-dot" style="background: #312e81;"></span>
               <span>夜班 {{ nightShiftCount }}</span>
             </div>
          </div>
        </div>
        <div class="divider"></div>

        <div class="panel-title">人员分布（本月）</div>
        <div class="text-muted text-sm">按被指派次数统计</div>
        <div class="bar-list">
          <div v-for="item in assigneeDistribution" :key="item.label" class="bar-row">
            <div class="bar-label">{{ item.label }}</div>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(item.value) }"></div>
            </div>
            <div class="bar-value">{{ item.value }}</div>
          </div>
          <div v-if="assigneeDistribution.length === 0" class="text-muted text-sm">暂无人员分布</div>
        </div>
        <div class="divider"></div>
        <div class="panel-title">科室分布（本月）</div>
        <div class="dept-list">
          <div v-for="item in departmentDistribution" :key="item.label" class="dept-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <div v-if="departmentDistribution.length === 0" class="text-muted text-sm">暂无科室分布</div>
        </div>
      </div>
    </div>

    <div v-if="showEditModal" class="modal-mask" @click.self="closeEditModal">
      <div class="modal-panel">
        <div class="modal-header">
          <div style="display:flex;align-items:center;gap:10px;">
            <button class="btn-delete" @click="handleDelete">删除</button>
            <h3>编辑班次 #{{ editForm.id }}</h3>
          </div>
          <button class="modal-close" @click="closeEditModal">×</button>
        </div>
        <div class="modal-body">
          <label>科室ID</label>
          <input v-model="editForm.departmentId" type="number" min="1" />

          <label>指派用户</label>
          <select v-model="editForm.assigneeUserId" class="form-select">
            <option :value="''">未指派</option>
            <option v-for="u in props.users" :key="u.id" :value="u.id">{{ u.fullName || u.name || u.email }}</option>
          </select>

          <label>必需角色</label>
          <input v-model="editForm.requiredRole" type="text" />

          <label>班次类型</label>
          <select v-model="editForm.shiftType">
            <option value="DAY">DAY</option>
            <option value="NIGHT">NIGHT</option>
          </select>

          <label>状态</label>
          <select v-model="editForm.status">
            <option value="OPEN">OPEN</option>
            <option value="ASSIGNED">ASSIGNED</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="CANCELLED">CANCELLED</option>
          </select>

          <label>开始时间</label>
          <input v-model="editForm.startTime" type="datetime-local" />

          <label>结束时间</label>
          <input v-model="editForm.endTime" type="datetime-local" />

          <label>备注</label>
          <input v-model="editForm.notes" type="text" placeholder="可选，填写本次调整说明" />

          <p v-if="editError" class="form-error">{{ editError }}</p>
        </div>
        <div class="modal-footer">
          <button class="btn-ghost" @click="closeEditModal">取消</button>
          <button class="btn-primary" :disabled="savingEdit" @click="submitEdit">{{ savingEdit ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>
    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="modal-mask" @click.self="showDeleteConfirm = false">
      <div class="modal-panel" style="max-width:360px;">
        <div class="modal-header">
          <h3>确认删除</h3>
        </div>
        <div class="modal-body" style="padding:16px;">
          <p style="margin:0;font-size:14px;color:#374151;">确定要删除班次 #{{ editForm.id }} 吗？此操作不可撤销。</p>
        </div>
        <div class="modal-footer">
          <button class="btn-ghost" @click="showDeleteConfirm = false">取消</button>
          <button class="btn-danger" @click="confirmDelete">确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import TemplateSelector from './TemplateSelector.vue';

const props = defineProps({
  shifts: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  departments: { type: Array, default: () => [] },
  users: { type: Array, default: () => [] },
  assigneeDistribution: { type: Array, default: () => [] },
  departmentDistribution: { type: Array, default: () => [] },
  isAdmin: { type: Boolean, default: false },
  onDeleteShift: { type: Function, default: null },
  onEditShift: { type: Function, default: null },
  onCreateShift: { type: Function, default: null }
});

const emit = defineEmits(['refresh', 'edit-shift', 'create-shift', 'delete-shift', 'openLeaveForm', 'openSwapForm']);

const showTemplateModal = ref(false);

const filterDeptId = ref('');
const filterStatus = ref('');
const filterShiftType = ref('');

const statusOptions = ['OPEN', 'ASSIGNED', 'COMPLETED', 'CANCELLED'];
const statusLabel = (status) => {
  const map = {
    OPEN: '待指派',
    ASSIGNED: '已指派',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    PENDING: '待指派'
  };
  return map[status] || (status ? status.replace(/_/g, ' ') : '未知');
};

const filteredShifts = computed(() => {
  return (props.shifts || []).filter(shift => {
    if (filterDeptId.value && String(shift.departmentId) !== String(filterDeptId.value)) return false;
    if (filterStatus.value && shift.status !== filterStatus.value) return false;
    if (filterShiftType.value && shift.shiftType !== filterShiftType.value) return false;
    return true;
  });
});

const assignedCount = computed(() => filteredShifts.value.filter(s => !!s.assigneeUserId).length);
const unassignedCount = computed(() => filteredShifts.value.filter(s => !s.assigneeUserId).length);
const nightShiftCount = computed(() => filteredShifts.value.filter(s => s.shiftType === 'NIGHT').length);
const dayShiftCount = computed(() => filteredShifts.value.length - nightShiftCount.value);

const resetFilters = () => {
  filterDeptId.value = '';
  filterStatus.value = '';
  filterShiftType.value = '';
};

// 应用模板（批量）
const applyTemplateBatch = async (shifts) => {
  showTemplateModal.value = false;
  if (!shifts || shifts.length === 0) return;

  let created = 0;
  let failed = 0;

  if (props.onCreateShift) {
    for (const shift of shifts) {
      try {
        await props.onCreateShift(shift);
        created++;
      } catch (e) {
        console.error('班次创建失败:', shift, e);
        failed++;
      }
    }
  }

  console.log(`模板创建完成: 成功 ${created}, 失败 ${failed}`);

  setTimeout(() => {
    emit('refresh');
  }, 500);
};

const pieChartStyle = computed(() => {
  const total = filteredShifts.value.length || 1;
  const nightPercent = (nightShiftCount.value / total) * 100;
  // 夜班深紫色 #312e81，白班浅紫色 #a855f7
  return {
    background: `conic-gradient(
      #312e81 0% ${nightPercent}%, 
      #a855f7 ${nightPercent}% 100%
    )`
  };
});

// Calculate thickness color roughly darker
const pieChartThicknessStyle = computed(() => {
    const total = filteredShifts.value.length || 1;
    const nightPercent = (nightShiftCount.value / total) * 100;
    // Darker shades for thickness
    return {
      background: `conic-gradient(
        #1e1b4b 0% ${nightPercent}%, 
        #7e22ce ${nightPercent}% 100%
      )`
    };
});

const formatTime = (isoString) => {
  if (!isoString) return '-';
  return new Date(isoString).toLocaleString();
};

const barWidth = (value) => {
  const values = props.assigneeDistribution.map(item => item.value || 0);
  const max = Math.max(1, ...values);
  const ratio = Math.min(1, (value || 0) / max);
  return `${Math.round(ratio * 100)}%`;
};

// Admin edit modal logic
const showEditModal = ref(false);
const showDeleteConfirm = ref(false);
const savingEdit = ref(false);
const editError = ref('');
const editForm = reactive({
  id: null,
  departmentId: '',
  assigneeUserId: '',
  requiredRole: '',
  shiftType: 'DAY',
  status: 'OPEN',
  startTime: '',
  endTime: '',
  notes: ''
});

const toLocalInput = (isoString) => {
  if (!isoString) return '';
  const d = new Date(isoString);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

const toIsoOrNull = (val) => {
  if (!val) return null;
  const d = new Date(val);
  if (Number.isNaN(d.getTime())) return null;
  return d.toISOString();
};

const openEditModal = (shift) => {
  editError.value = '';
  editForm.id = shift.id;
  editForm.departmentId = shift.departmentId ?? '';
  editForm.assigneeUserId = shift.assigneeUserId ?? '';
  editForm.requiredRole = shift.requiredRole ?? '';
  editForm.shiftType = shift.shiftType || 'DAY';
  editForm.status = shift.status || 'OPEN';
  editForm.startTime = toLocalInput(shift.startTime);
  editForm.endTime = toLocalInput(shift.endTime);
  editForm.notes = shift.notes ?? '';
  showEditModal.value = true;
};

const closeEditModal = () => {
  showEditModal.value = false;
  savingEdit.value = false;
  editError.value = '';
};

const submitEdit = async () => {
  editError.value = '';
  if (!editForm.id || !editForm.departmentId) {
    editError.value = '请补全班次ID和科室ID';
    return;
  }
  const start = toIsoOrNull(editForm.startTime);
  const end = toIsoOrNull(editForm.endTime);
  if (!start || !end || new Date(start).getTime() >= new Date(end).getTime()) {
    editError.value = '时间范围不合法';
    return;
  }

  // 有指派用户时自动设为已指派，无指派时设为待指派
  const autoStatus = editForm.assigneeUserId ? 'ASSIGNED' : 'OPEN';

  savingEdit.value = true;
  try {
    const payload = {
      id: Number(editForm.id),
      departmentId: Number(editForm.departmentId),
      assigneeUserId: editForm.assigneeUserId ? Number(editForm.assigneeUserId) : null,
      requiredRole: editForm.requiredRole || null,
      shiftType: editForm.shiftType || 'DAY',
      status: autoStatus,
      startTime: start,
      endTime: end,
      notes: editForm.notes?.trim() || null
    };
    if (props.onEditShift) {
      await props.onEditShift(payload);
    } else {
      emit('edit-shift', payload);
    }
    closeEditModal();
  } catch (e) {
    editError.value = e?.message || '保存失败';
  } finally {
    savingEdit.value = false;
  }
};

const handleDelete = async () => {
  showDeleteConfirm.value = true;
};

const confirmDelete = async () => {
  showDeleteConfirm.value = false;
  try {
    if (props.onDeleteShift) {
      await props.onDeleteShift(Number(editForm.id));
    } else {
      emit('delete-shift', Number(editForm.id));
    }
    closeEditModal();
  } catch (e) {
    editError.value = e?.message || '删除失败';
  }
};
</script>

<style scoped>
.btn-edit { border: 1px solid #9a8cff; color: #5b49d6; background: #fff; border-radius: 10px; padding: 6px 10px; cursor: pointer; }
.btn-delete { background: #fee2e2; color: #dc2626; border: 1px solid #fca5a5; border-radius: 10px; padding: 6px 14px; cursor: pointer; font-size: 13px; font-weight: 500; transition: all 0.2s; }
.btn-delete:hover { background: #fecaca; box-shadow: 0 2px 8px rgba(220,38,38,0.2); }
.btn-danger { background: #dc2626; color: #fff; border: none; border-radius: 10px; padding: 8px 14px; cursor: pointer; font-size: 13px; font-weight: 500; transition: all 0.2s; }
.btn-danger:hover { background: #b91c1c; }
.btn-template { 
  background: linear-gradient(135deg, #6366f1, #8b5cf6); 
  color: white; 
  border: none; 
  border-radius: 10px; 
  padding: 8px 16px; 
  cursor: pointer; 
  font-size: 13px; 
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transition: all 0.2s ease;
}
.btn-template:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}
.btn-leave {
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
  border: none;
  border-radius: 10px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
  transition: all 0.2s ease;
}
.btn-leave:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.4);
}
.btn-swap {
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
  border: none;
  border-radius: 10px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
  transition: all 0.2s ease;
}
.btn-swap:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 158, 11, 0.4);
}
.modal-mask { position: fixed; inset: 0; background: rgba(27, 21, 63, 0.25); display: flex; align-items: center; justify-content: center; z-index: 60; }
.modal-panel { width: min(640px, 92vw); background: #fff; border-radius: 16px; box-shadow: 0 20px 60px rgba(66, 44, 160, 0.25); overflow: hidden; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid #eee8ff; }
.modal-close { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #6b5bd2; }
.modal-body { display: grid; gap: 8px; padding: 16px; }
.modal-body input, .modal-body select { border: 1px solid #dcd4ff; border-radius: 10px; padding: 9px 10px; }
.form-select { width: 100%; border: 1px solid #dcd4ff; border-radius: 10px; padding: 9px 10px; font-size: 14px; background: white; }
.form-error { color: #d23b5f; font-size: 13px; margin-top: 4px; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px 16px; }
.btn-ghost { background: #fff; border: 1px solid #cdc5ff; color: #5b49d6; border-radius: 10px; padding: 8px 14px; }
.btn-primary { background: linear-gradient(135deg, #9b8bff, #7b6dff); border: none; color: #fff; border-radius: 10px; padding: 8px 14px; }
</style>
