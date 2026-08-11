# Railway 部署指南

## 环境变量配置

在 Railway 项目设置中添加以下环境变量：

### 必需变量
```
SPRING_PROFILES_ACTIVE=prod
PORT=9090
JAVA_OPTS=-Xms256m -Xmx384m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai
```

### 数据库配置（使用外部 PostgreSQL）
```
SPRING_DATASOURCE_URL=jql:postgresql://your-host:5432/your-db
SPRING_DATASOURCE_USERNAME=your-username
SPRING_DATASOURCE_PASSWORD=your-password
SPRING_DATASOURCE_DRIVER-CLASS-NAME=org.postgresql.Driver
```

### 应用配置
```
JWT_SECRET=your-256-bit-secret-string
JWT_EXPIRATION_MINUTES=60
CORS_ALLOWED_ORIGINS=https://your-railway-app.up.railway.app
WS_ALLOWED_ORIGINS=https://your-railway-app.up.railway.app
INIT_ADMIN_ENABLED=true
INIT_ADMIN_EMAIL=admin@yourdomain.com
INIT_ADMIN_PASSWORD=your-secure-password
INIT_ADMIN_FULL_NAME=System Admin
ANALYTICS_ENABLED=false
```

### LLM 配置（可选）
```
LLM_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
LLM_API_KEY=your-api-key
LLM_MODEL=ep-20260811021418-cxvdz
LLM_MAX_TOKENS=1024
LLM_TEMPERATURE=0.7
LLM_TIMEOUT=60
```

## 部署步骤

1. 将代码推送到 GitHub
2. 在 Railway 创建新项目
3. 连接 GitHub 仓库
4. 设置上述环境变量
5. Railway 会自动检测 Dockerfile 并构建

## 免费数据库选项

### Supabase（推荐）
- 访问 https://supabase.com
- 创建免费项目
- 获取连接字符串

### Neon
- 访问 https://neon.tech
- 创建免费项目
- 获取连接字符串

## 注意事项

1. Railway 免费层每月 $5 额度
2. 应用会自动休眠以节省额度
3. 冷启动可能需要 30-60 秒
4. 确保 JWT_SECRET 设置为安全的随机字符串
