import {
  AlertTriangle, ArrowLeft, Bold, Bug, Building2, Calendar, Check, ChevronLeft, ChevronRight, ClipboardList, Clock, Code,
  Copy, Download, ExternalLink, Eye, FileDown, FileText, Filter, Heading1, Heading2, Heading3, Highlighter, History, Image,
  Info, Italic, KeyRound, LayoutDashboard, LayoutGrid, Link, List, ListOrdered, Lock, LogOut, Maximize2, Minimize2, Minus,
  Moon, Palette, Pencil, Play, Plus, Printer, Quote, Redo2, Rows3, ScrollText, Search, SeparatorHorizontal, Settings, Shield,
  Square, SquareCode, Strikethrough, Sun, Table, Tag, Trash2, Underline, Undo2, Upload, User, Users, X, ZoomIn, ZoomOut,
} from 'lucide';

type IconNode = [string, Record<string, string | number>][];

/** Icons used by the app (explicit list keeps the bundle small; a typo fails the build). */
export const ICONS: Record<string, IconNode> = {
  AlertTriangle, ArrowLeft, Bold, Bug, Building2, Calendar, Check, ChevronLeft, ChevronRight, ClipboardList, Clock, Code,
  Copy, Download, ExternalLink, Eye, FileDown, FileText, Filter, Heading1, Heading2, Heading3, Highlighter, History, Image,
  Info, Italic, KeyRound, LayoutDashboard, LayoutGrid, Link, List, ListOrdered, Lock, LogOut, Maximize2, Minimize2, Minus,
  Moon, Palette, Pencil, Play, Plus, Printer, Quote, Redo2, Rows3, ScrollText, Search, SeparatorHorizontal, Settings, Shield,
  Square, SquareCode, Strikethrough, Sun, Table, Tag, Trash2, Underline, Undo2, Upload, User, Users, X, ZoomIn, ZoomOut,
} as unknown as Record<string, IconNode>;
