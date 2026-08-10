<template>
  <div v-if="show" class="modal-mask" @click.self="$emit('close')">
    <div class="modal-panel" style="max-width:520px;">
      <div class="modal-header">
        <h3>换班申请</h3>
        <button class="modal-close" @click="$emit('close')">×</button>
      </div>
      <div class="modal-body">
        <label>我的班次（换出）</label>
        <select v-model="form.shiftId" class="form-input">
          <option value="">请选择要换出的班次</option>
          <option v-for="s in myShifts" :key="s.id" :value="s.id">
            {{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }} ({{ s.departmentName || '未分配' }})
          </option>
        </select>

        <div v-if="selectedShift" class="shift-preview">
          <div class="preview-row">
            <span class="preview-label">班次时间</span>
            <span>{{ formatTime(selectedShift.startTime) }} - {{ formatTime(selectedShift.endTime) }}</span>
          </div>
          <div class="preview-row">
            <span class="preview-label">科室</span>
            <span>{{ selectedShift.departmentName || '未分配' }}</span>
          </div>
        </div>

        <label>目标班次（换入）</label>
        <select v-model="form.targetShiftId" class="form-input">
          <option value="">请选择要换入的班次</option>
          <option v-for="s in availableTargets" :key="s.id" :value="s.id">
            {{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }} ({{ s.departmentName || '未分配' }})
          </option>
        </select>

        <div v-if="selectedTarget" class="shift-preview target-preview">
          <div class="preview-row">
            <span class="preview-label">目标时间</span>
            <span>{{ formatTime(selectedTarget.startTime) }} - {{ formatTime(selectedTarget.endTime) }}</span>
          </div>
          <div class="preview-row">
            <span class="preview-label">科室</span>
            <span>{{ selectedTarget.departmentName || '未分配' }}</span>
          </div>
        </div>

        <label>换班原因</label>
        <textarea v-model="form.reason" class="form-input" rows="3" placeholder="请填写换班原因（选填）"></textarea>

        <p v-if="error" class="form-error">{{ error }}</p>
      </div>
      <div class="modal-footer">
        <button class="btn-ghost" @click="$emit('close')">取消</button>
        <button class="btn-primary btn-submit" :disabled="submitting || !form.shiftId || !form.targetShiftId" @click="submit">
          {{ submitting ? '提交中...' : '提交申请' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'

const props = defineProps({
  show: Boolean,
  myShifts: { type: Array, default: () => [] },
  onSubmit: { type: Function, default: null }
})

const emit = defineEmits(['close'])

const form = reactive({
  shiftId: '',
  targetShiftId: '',
  reason: ''
})

const submitting = ref(false)
const error = ref('')

const selectedShift = computed(() => {
  if (!form.shiftId) return null
  return props.myShifts.find(s => s.id === Number(form.shiftId))
})

const selectedTarget = computed(() => {
  if (!form.targetShiftId) return null
  return props.myShifts.find(s => s.id === Number(form.targetShiftId))
})

const availableTargets = computed(() => {
  return props.myShifts.filter(s => s.id !== Number(form.shiftId))
})

function formatTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

async function submit() {
  if (!form.shiftId) {
    error.value = '请选择要换出的班次'
    return
  }
  if (!form.targetShiftId) {
    error.value = '请选择要换入的目标班次'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await props.onSubmit({
      shiftId: Number(form.shiftId),
      requestType: 'SWAP',
      targetShiftId: Number(form.targetShiftId),
      reason: form.reason?.trim() || null
    })
    form.shiftId = ''
    form.targetShiftId = ''
    form.reason = ''
    emit('close')
  } catch (e) {
    error.value = e?.message || '提交失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: rgba(27,21,63,0.25); display: flex; align-items: center; justify-content: center; z-index: 60; }
.modal-panel { width: min(520px, 92vw); background: #fff; border-radius: 16px; box-shadow: 0 20px 60px rgba(66,44,160,0.25); overflow: hidden; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid #eee8ff; }
.modal-header h3 { margin: 0; font-size: 16px; color: #1e1b4b; }
.modal-close { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #6b5bd2; }
.modal-body { display: flex; flex-direction: column; gap: 8px; padding: 16px; }
.modal-body label { font-size: 13px; font-weight: 600; color: #374151; margin-top: 4px; }
.form-input { border: 1px solid #dcd4ff; border-radius: 10px; padding: 9px 10px; font-size: 14px; width: 100%; box-sizing: border-box; }
.form-input:focus { outline: none; border-color: #f59e0b; box-shadow: 0 0 0 3px rgba(245,158,11,0.1); }
.shift-preview { background: #fffbeb; border-radius: 10px; padding: 12px; margin-top: 4px; border: 1px solid #fde68a; }
.target-preview { background: #f0fdf4; border-color: #bbf7d0; }
.preview-row { display: flex; justify-content: space-between; font-size: 13px; color: #374151; padding: 4px 0; }
.preview-label { font-weight: 600; color: #6b7280; }
.form-error { color: #d23b5f; font-size: 13px; margin: 0; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px 16px; }
.btn-ghost { background: #fff; border: 1px solid #cdc5ff; color: #5b49d6; border-radius: 10px; padding: 8px 14px; cursor: pointer; }
.btn-submit { background: linear-gradient(135deg, #f59e0b, #d97706); }
.btn-submit:hover { box-shadow: 0 4px 12px rgba(245,158,11,0.3); }
.btn-primary { border: none; color: #fff; border-radius: 10px; padding: 8px 14px; cursor: pointer; font-weight: 500; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
