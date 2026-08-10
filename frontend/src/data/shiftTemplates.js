/**
 * 医院排班模板库
 * 8种常用排班模板
 */

export const shiftTemplates = [
  {
    id: 'three-shift',
    name: '三班倒',
    description: '24小时覆盖，每班8小时，三班轮换',
    icon: '🔄',
    shifts: [
      { name: '早班', startTime: '08:00', endTime: '16:00', requiredRole: 'DOCTOR' },
      { name: '中班', startTime: '16:00', endTime: '00:00', requiredRole: 'DOCTOR' },
      { name: '夜班', startTime: '00:00', endTime: '08:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '3天一轮',
    applicable: ['急诊科', 'ICU', '住院部']
  },
  {
    id: 'two-shift',
    name: '两班倒',
    description: '白班+夜班，每班12小时',
    icon: '🌙',
    shifts: [
      { name: '白班', startTime: '08:00', endTime: '20:00', requiredRole: 'DOCTOR' },
      { name: '夜班', startTime: '20:00', endTime: '08:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '2天一轮',
    applicable: ['急诊科', '重症监护']
  },
  {
    id: 'admin-shift',
    name: '行政班',
    description: '标准工作时间，周末双休',
    icon: '📋',
    shifts: [
      { name: '白班', startTime: '09:00', endTime: '17:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '周一至周五',
    applicable: ['门诊', '行政科室']
  },
  {
    id: 'four-shift',
    name: '四班三运转',
    description: '四个班组轮换，保证休息',
    icon: '🔄',
    shifts: [
      { name: '早班', startTime: '08:00', endTime: '16:00', requiredRole: 'NURSE' },
      { name: '中班', startTime: '16:00', endTime: '00:00', requiredRole: 'NURSE' },
      { name: '夜班', startTime: '00:00', endTime: '08:00', requiredRole: 'NURSE' },
      { name: '休息', startTime: '08:00', endTime: '08:00', requiredRole: 'NURSE' }
    ],
    cycle: '4天一轮',
    applicable: ['护理部', '住院部']
  },
  {
    id: 'weekend-shift',
    name: '周末值班',
    description: '周末加强班，保障急诊',
    icon: '📅',
    shifts: [
      { name: '周六白班', startTime: '08:00', endTime: '16:00', requiredRole: 'DOCTOR' },
      { name: '周六夜班', startTime: '16:00', endTime: '08:00', requiredRole: 'DOCTOR' },
      { name: '周日白班', startTime: '08:00', endTime: '16:00', requiredRole: 'DOCTOR' },
      { name: '周日夜班', startTime: '16:00', endTime: '08:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '每周',
    applicable: ['急诊科', '手术室']
  },
  {
    id: 'morning-shift',
    name: '早班+中班',
    description: '日间双班，覆盖就诊高峰',
    icon: '☀️',
    shifts: [
      { name: '早班', startTime: '07:00', endTime: '13:00', requiredRole: 'DOCTOR' },
      { name: '中班', startTime: '13:00', endTime: '19:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '每日',
    applicable: ['门诊', '检验科']
  },
  {
    id: 'night-shift',
    name: '夜班专岗',
    description: '专职夜班，保障夜间医疗',
    icon: '🌃',
    shifts: [
      { name: '前半夜', startTime: '20:00', endTime: '02:00', requiredRole: 'DOCTOR' },
      { name: '后半夜', startTime: '02:00', endTime: '08:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '每日',
    applicable: ['急诊科', 'ICU']
  },
  {
    id: 'on-call',
    name: '听班模式',
    description: '待命状态，紧急呼叫到岗',
    icon: '📞',
    shifts: [
      { name: '听班', startTime: '08:00', endTime: '08:00', requiredRole: 'DOCTOR' }
    ],
    cycle: '24小时',
    applicable: ['外科', '麻醉科']
  }
]

export function getTemplateById(id) {
  return shiftTemplates.find(t => t.id === id)
}

export function getAllTemplates() {
  return shiftTemplates
}
