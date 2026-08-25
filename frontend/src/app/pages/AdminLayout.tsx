import { NavLink, Outlet, useNavigate } from 'react-router';

const TABS = [
  { to: '/admin/dashboard', label: '대시보드' },
  { to: '/admin/continents', label: '영토 구성' },
  { to: '/admin/auctions', label: '경매 관리' },
  { to: '/admin/seasons', label: '시즌 운영' },
  { to: '/admin/items', label: '아이템 관리' },
  { to: '/admin/buildings', label: '건물 관리' },
  { to: '/admin/units', label: '유닛 관리' },
  { to: '/admin/users', label: '사용자 관리' },
  { to: '/admin/chat', label: '채팅 검열' },
  { to: '/admin/announcement', label: '공지 배너' },
  { to: '/admin/audit-logs', label: '감사 로그' },
];

export function AdminLayout() {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    navigate('/admin/login');
  };

  return (
    <div className="h-screen flex flex-col bg-surface text-foreground">
      <header className="flex items-center justify-between px-5 py-3 border-b border-outline">
        <div className="flex items-center gap-6">
          <h1 className="font-bold text-sm">🛡️ 관리자</h1>
          <nav className="flex gap-1">
            {TABS.map(t => (
              <NavLink
                key={t.to}
                to={t.to}
                className={({ isActive }) =>
                  `px-3 py-1.5 rounded-lg text-xs font-semibold ${
                    isActive ? 'bg-elevated text-foreground' : 'text-muted hover:text-foreground-soft'
                  }`
                }
              >
                {t.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <button onClick={handleLogout} className="text-muted text-xs hover:text-foreground-soft">
          로그아웃
        </button>
      </header>

      <div className="flex-1 overflow-hidden">
        <Outlet />
      </div>
    </div>
  );
}
