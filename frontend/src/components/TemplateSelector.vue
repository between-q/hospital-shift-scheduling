<template>
  <div class="template-modal" v-if="show" @click.self="$emit('close')">
    <div class="template-modal-content">
      <div class="template-modal-header">
        <h3>{{ selectedTemplate ? '配置排班模板 - ' + selectedTemplate.name : '选择排班模板' }}</h3>
        <button class="template-close" @click="handleClose">×</button>
      </div>
      
      <!-- 第一步：选择模板 -->
      <div v-if="!selectedTemplate" class="template-list">
        <div 
          v-for="template in templates" 
          :key="template.id"
          class="template-card"
          @click="selectTemplate(template)"
        >
          <div class="template-icon">{{ template.icon }}</div>
          <div class="template-info">
            <div class="template-name">{{ template.name }}</div>
            <div class="template-desc">{{ template.description }}</div>
            <div class="template-meta">
              <span class="template-cycle">{{ template.cycle }}</span>
              <span class="template-shifts">{{ template.shifts.filter(s => s.name !== '休息').length }} 个班次</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 第二步：配置表单 -->
      <div v-else class="config-form">
        <div class="config-section">
          <label>科室</label>
          <select v-model="configForm.departmentId" class="config-input">
            <option v-for="dept in departments" :key="dept.id" :value="dept.id">{{ dept.name }}</option>
          </select>
        </div>

        <div class="config-section">
          <label>默认角色</label>
          <select v-model="configForm.requiredRole" class="config-input">
            <option value="DOCTOR">医生 (DOCTOR)</option>
            <option value="NURSE">护士 (NURSE)</option>
          </select>
        </div>

        <div class="config-section">
          <label>生成天数</label>
          <select v-model.number="configForm.days" class="config-input">
            <option :value="3">3 天</option>
            <option :value="7">7 天</option>
            <option :value="14">14 天</option>
            <option :value="30">30 天</option>
          </select>
        </div>

        <div class="config-section">
          <label>起始日期</label>
          <input v-model="configForm.startDate" type="date" class="config-input" />
        </div>

        <!-- 班次预览表格 -->
        <div class="preview-section">
          <div class="preview-title">班次预览（共 {{ previewCount }} 个班次）</div>
          <div class="preview-table-wrap">
            <table class="preview-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>班次名称</th>
                  <th>开始时间</th>
                  <th>结束时间</th>
                  <th>角色</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, idx) in previewRows" :key="idx">
                  <td>{{ row.date }}</td>
                  <td>{{ row.name }}</td>
                  <td>{{ row.startTime }}</td>
                  <td>{{ row.endTime }}</td>
                  <td><span class="role-badge" :class="row.role === 'NURSE' ? 'role-nurse' : 'role-doctor'">{{ row.role === 'NURSE' ? '护士' : '医生' }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="config-actions">
          <button class="btn-back" @click="selectedTemplate = null">返回选择</button>
          <button class="btn-confirm" :disabled="creating" @click="confirmCreate">
            {{ creating ? '创建中...' : '确认创建 ' + previewCount + ' 个班次' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { shiftTemplates } from '../data/shiftTemplates.js'

const props = defineProps({
  show: Boolean,
  departments: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'apply'])

const templates = shiftTemplates
const selectedTemplate = ref(null)
const creating = ref(false)

const configForm = reactive({
  departmentId: null,
  requiredRole: 'DOCTOR',
  days: 7,
  startDate: new Date().toISOString().split('T')[0]
})

watch(() => props.show, (val) => {
  if (val) {
    selectedTemplate.value = null
    if (props.departments.length > 0) {
      configForm.departmentId = props.departments[0].id
    }
  }
})

watch(() => props.departments, (depts) => {
  if (depts.length > 0 && !configForm.departmentId) {
    configForm.departmentId = depts[0].id
  }
})

const previewRows = computed(() => {
  if (!selectedTemplate.value) return []
  const rows = []
  const start = new Date(configForm.startDate)
  const template = selectedTemplate.value

  for (let day = 0; day < configForm.days; day++) {
    const date = new Date(start)
    date.setDate(date.getDate() + day)
    const dateStr = `${date.getMonth() + 1}/${date.getDate()}`

    template.shifts.forEach(shift => {
      if (shift.name === '休息') return
      rows.push({
        date: dateStr,
        name: shift.name,
        startTime: shift.startTime,
        endTime: shift.endTime,
        role: configForm.requiredRole
      })
    })
  }
  return rows
})

const previewCount = computed(() => previewRows.value.length)

function selectTemplate(template) {
  selectedTemplate.value = template
}

function handleClose() {
  selectedTemplate.value = null
  emit('close')
}

async function confirmCreate() {
  if (!configForm.departmentId || !selectedTemplate.value) return
  creating.value = true

  const start = new Date(configForm.startDate)
  const shifts = []

  for (let day = 0; day < configForm.days; day++) {
    const date = new Date(start)
    date.setDate(date.getDate() + day)

    selectedTemplate.value.shifts.forEach(shift => {
      if (shift.name === '休息') return

      const startParts = shift.startTime.split(':').map(Number)
      const endParts = shift.endTime.split(':').map(Number)
      const isOvernight = endParts[0] < startParts[0] || (endParts[0] === startParts[0] && endParts[1] < startParts[1])

      let startDateObj = new Date(date)
      startDateObj.setHours(startParts[0], startParts[1], 0, 0)

      let endDateObj = new Date(date)
      endDateObj.setHours(endParts[0], endParts[1], 0, 0)
      if (isOvernight) endDateObj.setDate(endDateObj.getDate() + 1)

      const pad = (n) => String(n).padStart(2, '0')
      const fmtLocal = (d) => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`

      shifts.push({
        startTime: fmtLocal(startDateObj),
        endTime: fmtLocal(endDateObj),
        requiredRole: configForm.requiredRole,
        departmentId: Number(configForm.departmentId),
        notes: `${selectedTemplate.value.name} - ${shift.name}`
      })
    })
  }

  emit('apply', shifts)
  creating.value = false
  selectedTemplate.value = null
}
</script>

<style scoped>
.template-modal {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.template-modal-content {
  background: white;
  border-radius: 16px;
  width: 90%;
  max-width: 900px;
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.template-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
  background: white;
  z-index: 1;
}

.template-modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
}

.template-close {
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  color: #6b7280;
  padding: 4px 8px;
  border-radius: 4px;
  line-height: 1;
}

.template-close:hover {
  background: #f3f4f6;
  color: #1f2937;
}

/* 模板选择列表 */
.template-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 24px;
}

@media (max-width: 640px) {
  .template-list { grid-template-columns: 1fr; }
}

.template-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.template-card:hover {
  border-color: #6366f1;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
  transform: translateY(-2px);
}

.template-icon {
  font-size: 32px;
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
  border-radius: 12px;
}

.template-info { flex: 1; min-width: 0; }
.template-name { font-size: 16px; font-weight: 600; color: #1f2937; margin-bottom: 4px; }
.template-desc { font-size: 13px; color: #6b7280; margin-bottom: 8px; line-height: 1.4; }
.template-meta { display: flex; gap: 12px; font-size: 12px; }
.template-cycle { color: #6366f1; font-weight: 500; }
.template-shifts { color: #10b981; font-weight: 500; }

/* 配置表单 */
.config-form {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-section label {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.config-input {
  border: 1px solid #dcd4ff;
  border-radius: 10px;
  padding: 10px 12px;
  font-size: 14px;
  color: #1f2937;
  background: white;
  outline: none;
  transition: border-color 0.2s;
}

.config-input:focus {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

/* 预览表格 */
.preview-section {
  margin-top: 8px;
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 10px;
}

.preview-table-wrap {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  max-height: 300px;
  overflow-y: auto;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.preview-table th {
  background: #f9fafb;
  padding: 10px 12px;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
  position: sticky;
  top: 0;
}

.preview-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #f3f4f6;
  color: #4b5563;
}

.preview-table tr:last-child td {
  border-bottom: none;
}

.role-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
}

.role-doctor {
  background: #ede9fe;
  color: #6d28d9;
}

.role-nurse {
  background: #d1fae5;
  color: #065f46;
}

/* 操作按钮 */
.config-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.btn-back {
  background: white;
  border: 1px solid #cdc5ff;
  color: #5b49d6;
  border-radius: 10px;
  padding: 10px 20px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-back:hover {
  background: #f5f3ff;
}

.btn-confirm {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: white;
  border: none;
  border-radius: 10px;
  padding: 10px 24px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transition: all 0.2s;
}

.btn-confirm:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}

.btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
