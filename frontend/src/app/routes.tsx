import { lazy, type ComponentType } from 'react';
import { createBrowserRouter, Navigate } from 'react-router';

import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { AdminLoginPage } from './pages/AdminLoginPage';
import { PrivateRoute } from './components/PrivateRoute';
import { AdminRoute } from './components/AdminRoute';

const lazyPage = <K extends string>(loader: () => Promise<Record<K, ComponentType>>, key: K) =>
  lazy(() => loader().then(m => ({ default: m[key] })));

const WorldMapPage = lazyPage(() => import('./pages/WorldMapPage'), 'WorldMapPage');
const ContinentPage = lazyPage(() => import('./pages/ContinentPage'), 'ContinentPage');
const TerritoryDetailPage = lazyPage(() => import('./pages/TerritoryDetailPage'), 'TerritoryDetailPage');
const RankingPage = lazyPage(() => import('./pages/RankingPage'), 'RankingPage');
const ChargePage = lazyPage(() => import('./pages/ChargePage'), 'ChargePage');
const TerritoryGridPage = lazyPage(() => import('./pages/TerritoryGridPage'), 'TerritoryGridPage');
const SiegePage = lazyPage(() => import('./pages/SiegePage'), 'SiegePage');
const SiegesPage = lazyPage(() => import('./pages/SiegesPage'), 'SiegesPage');
const ItemShopPage = lazyPage(() => import('./pages/ItemShopPage'), 'ItemShopPage');
const SeasonPassPage = lazyPage(() => import('./pages/SeasonPassPage'), 'SeasonPassPage');
const VaultPage = lazyPage(() => import('./pages/VaultPage'), 'VaultPage');
const TerritoryManagementPage = lazyPage(() => import('./pages/TerritoryManagementPage'), 'TerritoryManagementPage');
const PersonalIslandPage = lazyPage(() => import('./pages/PersonalIslandPage'), 'PersonalIslandPage');
const SettingsPage = lazyPage(() => import('./pages/SettingsPage'), 'SettingsPage');
const GuildListPage = lazyPage(() => import('./pages/GuildListPage'), 'GuildListPage');
const GuildDetailPage = lazyPage(() => import('./pages/GuildDetailPage'), 'GuildDetailPage');
const NotificationPage = lazyPage(() => import('./pages/NotificationPage'), 'NotificationPage');
const AdminLayout = lazyPage(() => import('./pages/AdminLayout'), 'AdminLayout');
const AdminContinentPage = lazyPage(() => import('./pages/AdminContinentPage'), 'AdminContinentPage');
const AdminAuctionPage = lazyPage(() => import('./pages/AdminAuctionPage'), 'AdminAuctionPage');
const AdminUserListPage = lazyPage(() => import('./pages/AdminUserListPage'), 'AdminUserListPage');
const AdminUserDetailPage = lazyPage(() => import('./pages/AdminUserDetailPage'), 'AdminUserDetailPage');
const AdminChatPage = lazyPage(() => import('./pages/AdminChatPage'), 'AdminChatPage');
const AdminAuditLogPage = lazyPage(() => import('./pages/AdminAuditLogPage'), 'AdminAuditLogPage');
const AdminDashboardPage = lazyPage(() => import('./pages/AdminDashboardPage'), 'AdminDashboardPage');
const AdminSeasonPage = lazyPage(() => import('./pages/AdminSeasonPage'), 'AdminSeasonPage');
const AdminItemPage = lazyPage(() => import('./pages/AdminItemPage'), 'AdminItemPage');
const AdminAnnouncementPage = lazyPage(() => import('./pages/AdminAnnouncementPage'), 'AdminAnnouncementPage');
const AdminBuildingPage = lazyPage(() => import('./pages/AdminBuildingPage'), 'AdminBuildingPage');
const AdminUnitPage = lazyPage(() => import('./pages/AdminUnitPage'), 'AdminUnitPage');

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', Component: LoginPage },
  { path: '/register', Component: RegisterPage },
  { path: '/app/map', Component: WorldMapPage },
  { path: '/app/continent/:id', Component: ContinentPage },
  { path: '/app/territory/:id', Component: TerritoryDetailPage },
  { path: '/app/ranking', Component: RankingPage },
  { path: '/app/guild', Component: GuildListPage },
  { path: '/app/guild/:id', element: <PrivateRoute><GuildDetailPage /></PrivateRoute> },
  { path: '/app/notifications', element: <PrivateRoute><NotificationPage /></PrivateRoute> },
  { path: '/app/charge', element: <PrivateRoute><ChargePage /></PrivateRoute> },
  { path: '/app/territory-grid/:id', element: <PrivateRoute><TerritoryGridPage /></PrivateRoute> },
  { path: '/app/siege', element: <PrivateRoute><SiegePage /></PrivateRoute> },
  { path: '/app/sieges', element: <PrivateRoute><SiegesPage /></PrivateRoute> },
  { path: '/app/item-shop', element: <PrivateRoute><ItemShopPage /></PrivateRoute> },
  { path: '/app/season-pass', element: <PrivateRoute><SeasonPassPage /></PrivateRoute> },
  { path: '/app/vault', element: <PrivateRoute><VaultPage /></PrivateRoute> },
  { path: '/app/territory-management', element: <PrivateRoute><TerritoryManagementPage /></PrivateRoute> },
  { path: '/app/land-tax', element: <Navigate to="/app/territory-management?tab=tax" replace /> },
  { path: '/app/my-island', element: <PrivateRoute><PersonalIslandPage /></PrivateRoute> },
  { path: '/app/settings', element: <PrivateRoute><SettingsPage /></PrivateRoute> },
  { path: '/admin/login', Component: AdminLoginPage },
  {
    path: '/admin',
    element: <AdminRoute><AdminLayout /></AdminRoute>,
    children: [
      { index: true, element: <Navigate to="/admin/dashboard" replace /> },
      { path: 'dashboard', element: <AdminDashboardPage /> },
      { path: 'continents', element: <AdminContinentPage /> },
      { path: 'auctions', element: <AdminAuctionPage /> },
      { path: 'seasons', element: <AdminSeasonPage /> },
      { path: 'items', element: <AdminItemPage /> },
      { path: 'buildings', element: <AdminBuildingPage /> },
      { path: 'units', element: <AdminUnitPage /> },
      { path: 'users', element: <AdminUserListPage /> },
      { path: 'users/:id', element: <AdminUserDetailPage /> },
      { path: 'chat', element: <AdminChatPage /> },
      { path: 'announcement', element: <AdminAnnouncementPage /> },
      { path: 'audit-logs', element: <AdminAuditLogPage /> },
    ],
  },
  { path: '*', element: <Navigate to="/login" replace /> },
]);
