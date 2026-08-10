<template>
  <div v-if="show" class="modal-mask" @click.self="$emit('close')">
    <div class="modal-panel" style="max-width:520px;">
      <div class="modal-header">
        <h3>提交申请</h3>
        <button class="modal-close" @click="$emit('close')">×</button>
      </div>
      <div class="modal-body">
        <label>选择班次</label>
        <select v-model="form.shiftId" class="form-input">
          <option value="">请选择</option>
          <option v-for="s in myShifts" :key="s.id" :value="s.id">
            #{{ s.id }} {{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }} ({{ s.departmentName || '未分配' }})
          </option>
        </select>

        <label>申请类型</label>
        <div class="type-row">
          <button :class="['type-btn', form.requestType === 'LEAVE' && 'active']" @click="form.requestType = 'LEAVE'">请假</button>
          <button :class="['type-btn', form.requestType === 'SWAP' && 'active']" @click="form.requestType = 'SWAP'">换班</button>
        </div>

        <template v-if="form.requestType === 'SWAP'">
          <label>目标班次（换班对象）</label>
          <select v-model="form.targetShiftId" class="form-input">
            <option value="">请选择目标班次</option>
            <option v-for="s in availableTargets" :key="s.id" :value="s.id">
              #{{ s.id }} {{ formatTime(s.startTime) }} - {{ formatTime(s.endTime) }} ({{ s.departmentName || '未分配' }})
            </option>
          </select>
        </template>

        <label>申请理由</label>
        <textarea v-model="form.reason" class="form-input" rows="3" placeholder="请填写理由（选填）"></textarea>

        <p v-if="error" class="form-error">{{ error }}</p>
      </div>
      <div class="modal-footer">
        <button class="btn-ghost" @click="$emit('close')">取消</button>
        <button class="btn-primary" :disabled="submitting || !form.shiftId" @click="submit">
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
  requestType: 'LEAVE',
  targetShiftId: '',
  reason: ''
})

const submitting = ref(false)
const error = ref('')

const availableTargets = computed(() => {
  return props.myShifts.filter(s => s.id !== form.shiftId)
})

function formatTime(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}

async function submit() {
  if (!form.shiftId) return
  if (form.requestType === 'SWAP' && !form.targetShiftId) {
    error.value = '请选择目标班次'
    return
  }
  error.value = ''
  submitting.value = true
  try {
    await props.onSubmit({
      shiftId: Number(form.shiftId),
      requestType: form.requestType,
      targetShiftId: form.targetShiftId ? Number(form.targetShiftId) : null,
      reason: form.reason || null
    })
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
.modal-header h3 { margin: 0; font-size: 16px; }
.modal-close { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #6b5bd2; }
.modal-body { display: flex; flex-direction: column; gap: 8px; padding: 16px; }
.modal-body label { font-size: 13px; font-weight: 600; color: #374151; margin-top: 4px; }
.form-input { border: 1px solid #dcd4ff; border-radius: 10px; padding: 9px 10px; font-size: 14px; width: 100%; box-sizing: border-box; }
.type-row { display: flex; gap: 8px; }
.type-btn { flex: 1; padding: 8px; border: 2px solid #e5e7eb; border-radius: 10px; background: white; cursor: pointer; font-size: 13px; font-weight: 500; transition: all 0.2s; }
.type-btn.active { border-color: #6366f1; background: #f5f3ff; color: #5b49d6; }
.form-error { color: #d23b5f; font-size: 13px; margin: 0; }
.modal-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 12px 16px 16px; }
.btn-ghost { background: #fff; border: 1px solid #cdc5ff; color: #5b49d6; border-radius: 10px; padding: 8px 14px; cursor: pointer; }
.btn-primary { background: linear-gradient(135deg, #9b8bff, #7b6dff); border: none; color: #fff; border-radius: 10px; padding: 8px 14px; cursor: pointer; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
