/*
 * Copyright 2016. SHENQINCI(沈钦赐)<dev@blxt.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.blxt.markdowneditors.view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.blxt.markdowneditors.R;
import com.blxt.markdowneditors.adapter.FileListAdapter;
import com.blxt.markdowneditors.adapter.OnItemClickLitener;
import com.blxt.markdowneditors.base.BaseApplication;
import com.blxt.markdowneditors.base.BaseRefreshFragment;
import com.blxt.markdowneditors.engine.ActionModeCallback;
import com.blxt.markdowneditors.entity.FileBean;
import com.blxt.markdowneditors.event.RxEvent;
import com.blxt.markdowneditors.presenter.FolderManagerPresenter;
import com.blxt.markdowneditors.presenter.IFolderManagerView;
import com.blxt.markdowneditors.utils.Check;
import com.blxt.markdowneditors.utils.ViewUtils;
import com.blxt.markdowneditors.widget.TabView;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;

/**
 * 文件管理界面
 *
 * @author 沈钦赐
 * @date 16/1/27
 */
public class FolderFragment extends BaseRefreshFragment implements IFolderManagerView, View.OnClickListener, OnItemClickLitener {

    @Bind(R.id.content_view)
    protected RecyclerView mfileList;
    @Bind(R.id.tab_view)
    protected TabView mTabView;
    @Bind(R.id.noContent)
    protected View noContent;

    private FolderManagerPresenter mPresenter;

    private FloatingActionButton flbFabCreateFolder;
    private FloatingActionButton  flbFabCreateFile;
    private FloatingActionsMenu flbMenu;


    public static File file_select;
    private List<FileBean> files = new ArrayList<>();
    private FileListAdapter mAdapter;


    private ActionMode mActionMode;

    /** 文件粘贴模式ActionMode */
    private ActionMode.Callback pasteModeCallback;
    /** 文件编辑模式ActionMode */
    private ActionMode.Callback editModeCallback;

    @Override
    public void resume() {

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPresenter.detachView();//VP分离
        mPresenter = null;
    }

    @Override
    protected void onRefresh(SwipeRefreshLayout swipeRefreshLayout) {
        mPresenter.refreshCurrentPath();
        mPresenter.closeEditMode();
        // Log.i("重绘",getClass().getName());
        getActivity().invalidateOptionsMenu();
        super.refresh();

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_folder_manager;
    }

    @Override
    public void onCreateAfter(Bundle savedInstanceState) {
        super.onCreateAfter(savedInstanceState);
        initActionMode();
        //初始化Presenter
        mPresenter = new FolderManagerPresenter(files);
        mPresenter.attachView(this);


        //初始化recycleView
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mfileList.setLayoutManager(linearLayoutManager);
        mfileList.setAdapter(mAdapter = new FileListAdapter(mContext, files));
        mfileList.setItemAnimator(new DefaultItemAnimator());
        mfileList.setLongClickable(true);
        mAdapter.setOnItemClickLitener(this);
        //如果是ListView 多选可以用MultiChoiceModeListener会方便很多
    }

    @Override
    public void initData() {
        mPresenter.initRoot(mContext);

        flbMenu = rootView.findViewById(R.id.flb_menu);

        flbFabCreateFolder = rootView.findViewById(R.id.flb_fab_create_folder);
        flbFabCreateFile = rootView.findViewById(R.id.flb_fab_create_file);
        flbFabCreateFolder.setIcon(R.drawable.ic_action_create_folder);
        flbFabCreateFile.setIcon(R.drawable.ic_action_create_file_w_24);

        flbMenu.setOnClickListener(this);
        flbFabCreateFolder.setOnClickListener(this);
        flbFabCreateFile.setOnClickListener(this);


    }

    /**
     * 错误回调
     * On failure.
     *
     * @param errorCode the error code
     * @param message   the message
     * @param flag      the flag
     */
    @Override
    public void onFailure(int errorCode, String message, int flag) {
        switch (flag) {
            case CALL_GET_FILES://停止刷新，并提示失败原因
                finishRefresh();
            default:
                BaseApplication.showSnackbar(getSwipeRefreshLayout(), message);
                break;
        }

    }

    @Override
    public void showWait(String message, boolean canBack, int flag) {
        switch (flag) {
            case CALL_GET_FILES://获取文件列表
                getSwipeRefreshLayout().setRefreshing(true);
                break;
                default:
        }
    }

    @Override
    public void hideWait(int flag) {
        switch (flag) {
            case CALL_GET_FILES://获取文件列表
                finishRefresh();
                break;
                default:
        }
    }

    @Override
    public void getFileListSuccess(List<FileBean> files) {
        mAdapter.notifyDataSetChanged();

        if (files.isEmpty()) {
            noContent.setVisibility(View.VISIBLE);
        } else {
            noContent.setVisibility(View.GONE);
        }
        finishRefresh();
    }

    @Override
    public void otherSuccess(int flag) {
        switch (flag) {
            case CALL_COPY_PASTE://复制粘贴回调
            case CALL_CUT_PASTE://剪切粘贴回调
//                getSwipeRefreshLayout().postDelayed(()->refresh(),50);
                break;
            case CALL_PASTE_MODE://进入粘贴模式
                pasteMode();
                break;
            case CALL_CLOSE_PASTE_MODE://关闭粘贴模式粘贴模式
                closeActionMode();
                break;
            case CALL_EDIT_MODE://进入编辑模式
                openEditMode();
                break;
            case CALL_CLOSE_EDIT_MODE://关闭编辑模式
                closeEditMode();
                break;
            case CALL_REMOVE_TAB://移除标题
                removeTab();
                break;
                default:
        }
    }

    private void closeEditMode() {
        mAdapter.setEditMode(false);
        mAdapter.notifyDataSetChanged();
        closeActionMode();
    }

    private void openEditMode() {
        mAdapter.setEditMode(true);
        //打开编辑模式的ActionMode
        mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(editModeCallback);
        mActionMode.setTitle(String.valueOf(mPresenter.getSelectCount()));
    }

    private void closeActionMode() {
//        mAdapter.notifyDataSetChanged();
        if (mActionMode != null) {
            mActionMode.finish();
        }
        mActionMode = null;
    }

    private void pasteMode() {
        //打开粘贴模式的ActionMode
        mActionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(pasteModeCallback);
        mActionMode.setTitle("请选择粘贴位置");
    }

    @Override
    public void addTab(String title) {
        mTabView.addTab(title, this);
    }


    private boolean removeTab() {
        return mTabView.removeTab();
    }


    @Override
    public void updatePosition(int position, FileBean bean) {
        mAdapter.notifyItemChanged(position);
    }

    @Override
    public void addFilePosition(int position, FileBean bean) {
        mAdapter.addData(position, bean);
    }


    @Override
    public void onItemClick(View view, int position) {
        FileBean fileBean = files.get(position);

        //编辑模式下，这选择文件
        if (mPresenter.isEditMode() && mActionMode != null) {
            fileBean.isSelect = !fileBean.isSelect;
            mAdapter.notifyItemChanged(position);
            //算出当前选择数量，赋值到标题
            int selectCount = mPresenter.getSelectCount();
            //如果数量等于1，这显示重命名菜单，否则隐藏
            //如果数量为0，这关闭编辑模式
            if (selectCount == 0) {
                mActionMode.setTitle("");
                mPresenter.closeEditMode();
                this.refresh();
            } else if (selectCount == 1) {
                mActionMode.setTitle(String.valueOf(selectCount));
                mActionMode.getMenu().findItem(R.id.action_edit).setVisible(true);
            } else {
                mActionMode.setTitle(String.valueOf(selectCount));
                mActionMode.getMenu().findItem(R.id.action_edit).setVisible(false);
            }
            return;
        }

        //非编辑模式下
        if (fileBean.isDirectory) {//文件夹
            mPresenter.enterFolder(fileBean.absPath);
        } else {//文件
            file_select = new File(fileBean.absPath);
            Intent intent = new Intent(mContext, EditorActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            //设置数据URI与数据类型匹配
            intent.setDataAndType(Uri.fromFile(new File(fileBean.absPath)), "file");
            ViewUtils.startActivity(intent, getActivity(), view, EditorActivity.SHARED_ELEMENT_NAME);
        }


    }

    @Override
    public void onItemLongClick(View view, int position) {
        if (mPresenter.isEditMode()) {
            return;
        }

        FileBean fileBean = files.get(position);
        fileBean.isSelect = !fileBean.isSelect;
        mAdapter.notifyItemChanged(position);
        rename();
        /**取消文件夹编辑、剪辑等模式，只保留重命名操作*/
//        view.postDelayed(() ->
//                mPresenter.openEditMode(), 5);

    }


    @Override
    public void onClick(View v) {
        flbMenu.collapse();
        switch (v.getId()) {
            case R.id.file_name:
                Object tag = v.getTag(R.id.tag);
                if (tag != null && tag instanceof Integer) {//点击顶部导航
                    int index = ((Integer) tag).intValue();
                    mPresenter.backFolder(index);
                }
                break;
            case R.id.flb_fab_create_file: // 创建文件
                createNote();
                break;
            case R.id.flb_fab_create_folder: // 创建文件夹
                createFolder();
                break;
                default:
        }

    }

    @Override
    public boolean hasMenu() {
        return true;
    }


    /**
     *  普通模式
     * @param menu     the menu
     * @param inflater the inflater
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_folder_manager, menu);
        // Log.i("普通模式",getClass().getName());



        initSearchView(menu);
        super.onCreateOptionsMenu(menu, inflater);
    }




    /**
     * 初始化SearchView
     * Init search view.
     *
     * @param menu the menu
     */
    private SearchView searchView;
    boolean searchViewIsShow;

    private void initSearchView(Menu menu) {
        //SearchView相关
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                BaseApplication.showSnackbar(getSwipeRefreshLayout(), "" + s);
                if (!Check.isEmpty(s)) {
                    mPresenter.searchCurrentPath(s);
                }
                searchView.setIconified(false);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.length() == 0) {
                    mPresenter.refreshCurrentPath();
                }
                return false;
            }
        });
        searchView.setOnQueryTextFocusChangeListener((view, queryTextFocused) -> {
            if (queryTextFocused) {
                searchViewIsShow = true;
            }
        });

        AppCompatAutoCompleteTextView editText = (AppCompatAutoCompleteTextView) searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.action_create_folder:
//                createFolder();
//                break;
//            case R.id.action_create_file:
//                createNote();
//                break;
            default:
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 新建文件（文章）
     * Create file.
     */
    private void createNote() {
        Intent intent = new Intent(mContext, EditorActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        //设置数据URI与数据类型匹配
        String path = new File(mPresenter.currentPath()).getPath();
        intent.setDataAndType(Uri.fromFile(new File(path)), "file");
        mContext.startActivity(intent);

    }

    /**
     * 新建文件夹
     * Create folder.
     */
    public void createFolder() {
        //显示重命名对话框
        View rootView = LayoutInflater.from(mContext).inflate(R.layout.view_common_input_view, null);

        AlertDialog mInputDialog = new AlertDialog.Builder(mContext)
                .setTitle("新建文件夹")
                .setView(rootView)
                .show();

        TextInputLayout textInputLayout = (TextInputLayout) rootView.findViewById(R.id.inputHint);
        EditText text = (EditText) rootView.findViewById(R.id.text);
        textInputLayout.setHint("请输入文件夹名");
        rootView.findViewById(R.id.sure).setOnClickListener(v -> {
            String result = text.getText().toString().trim();

            if (Check.isEmpty(result)) {
                textInputLayout.setError("不能为空");
                return;
            }
            if (mPresenter.createFoloderIsExists(result)) {
                textInputLayout.setError("文件已经存在");
                return;
            }
            mPresenter.createFolder(result);
            mInputDialog.dismiss();
        });
        rootView.findViewById(R.id.cancel).setOnClickListener(v -> {
            mInputDialog.dismiss();
        });
        rootView.findViewById(R.id.delete).setVisibility(View.GONE);
        mInputDialog.show();
    }




    @Override
    public boolean onBackPressed() {//返回按钮
         Log.i("按钮", "返回");
        if(mPresenter.closeEditMode()){ // 关闭文件夹编辑模式

            this.refresh();

            return true;
        }else if (searchView != null && searchView.isShown() && searchViewIsShow) {//搜索菜单打开了
            searchView.onActionViewCollapsed();  //关闭ActionView(SearchView)
            searchView.setQuery("", false);       //清空输入框
            getActivity().supportInvalidateOptionsMenu();//恢复
            searchViewIsShow = false;
            return true;
        }
        else if(mPresenter.backFolder()){
            Log.i("返回按钮","返回上一级");
            return true;
        }
        else{
           return super.onBackPressed();
        }
    }


    /**
     * 编辑模式
     * 初始化的CallBack
     * Init action mode.
     */
    private void initActionMode() {
        pasteModeCallback = new ActionModeCallback(getActivity(), R.color.colorPrimary) {
            @Override
            public void onDestroyActionModeCustom(ActionMode mode) {
                mActionMode = null;
                // Log.i("编辑模式","pasteModeCallback-onDestroyActionModeCustom");
            }

            @Override
            public boolean onCreateActionModeCustom(ActionMode mode, Menu menu) {
                // Log.i("编辑模式","pasteModeCallback-onCreateActionModeCustom");
                MenuInflater inflater = mode.getMenuInflater();
                mode.setTitle("普通模式");
               // inflater.inflate(R.menu.menu_action_paste, menu);
                menu.removeItem(R.id.action_edit);
                menu.removeItem(R.id.action_delete);
                menu.removeItem(R.id.action_copy);
                menu.removeItem(R.id.action_cut);
                inflater.inflate(R.menu.menu_folder_manager, menu);
                // Log.i("普通模式",getClass().getName());

                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // Log.i("编辑模式","pasteModeCallback-onActionItemClicked");
                boolean flag = false;
//                switch (item.getItemId()) {
//                    case R.id.action_paste:
//                        mPresenter.paste();
//                        flag = true;
//                        break;
//                    case R.id.action_create_folder:
//                        createFolder();
//                        flag = true;
//                        break;
//                        default:
//                }
                return flag;
            }

        };
        editModeCallback = new ActionModeCallback(getActivity(), R.color.colorPrimary) {
            @Override
            public void onDestroyActionModeCustom(ActionMode mode) {
                // Log.i("编辑模式","editModeCallback-onDestroyActionModeCustom");
                mPresenter.closeEditMode();
                refresh();
                mActionMode = null;
            }

            @Override
            public boolean onCreateActionModeCustom(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();

                inflater.inflate(R.menu.menu_action_folder, menu);
                // Log.i("编辑模式",getClass().getName());
                menu.findItem(R.id.action_edit).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.findItem(R.id.action_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.findItem(R.id.action_copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                menu.findItem(R.id.action_cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // Log.i("编辑模式","editModeCallback-onActionItemClicked");
                boolean ret = false;
                switch (item.getItemId()) {
                    case R.id.action_edit:
                        rename();
                        break;
                    case R.id.action_delete:
                        deleteFiles();
                        break;
                    case R.id.action_copy:
                        coptFiles();
                        break;
                    case R.id.action_cut:
                        cutFiles();
                        break;
                        default:
                }
                return ret;
            }

        };
    }


    /**
     * Re name.
     * 重命名文件、文件夹
     */
    private void rename() {
        FileBean selectBean = mPresenter.getSelectBean();
        if (selectBean == null) {
            return;
        }

        //显示重命名对话框
        View rootView = LayoutInflater.from(mContext).inflate(R.layout.view_common_input_view, null);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle("重命名")
                .setView(rootView)
                .show();

        TextInputLayout textInputLayout = (TextInputLayout) rootView.findViewById(R.id.inputHint);
        EditText text = (EditText) rootView.findViewById(R.id.text);
        text.setText(selectBean.name);
        text.setSelection(0, selectBean.isDirectory ? selectBean.name.length() : selectBean.name.lastIndexOf("."));
        textInputLayout.setHint("请输入" + (selectBean.isDirectory ? "文件夹名" : "文件名"));
        rootView.findViewById(R.id.sure).setOnClickListener(v -> {
            String result = text.getText().toString().trim();
            mPresenter.refreshCurrentPath();
            if (!selectBean.isDirectory &&
                    !result.endsWith(".md") &&
                    !result.endsWith(".markdown") &&
                    !result.endsWith(".markd")) {
                textInputLayout.setError("文件后缀名必须为：md|markdown|markd");
                return;
            }
            if (Check.isEmpty(result)) {
                textInputLayout.setError("不能为空");
                return;
            }
            if (!selectBean.isDirectory && mPresenter.fileIsExists(result)) {
                textInputLayout.setError("文件已经存在");
                return;
            }
            if (selectBean.isDirectory && mPresenter.createFoloderIsExists(result)) {
                textInputLayout.setError("文件夹已经存在");
                return;
            }

            if (!mPresenter.rename(selectBean, result)) {
                textInputLayout.setError("重命名失败了");
                return;
            }

            textInputLayout.setErrorEnabled(false);

            if (mActionMode != null) {
                mActionMode.finish();
            }
            dialog.dismiss();
        });

        rootView.findViewById(R.id.cancel).setOnClickListener(v -> {
            mPresenter.refreshCurrentPath();
            dialog.dismiss();
        });


        rootView.findViewById(R.id.delete).setOnClickListener(v -> {
            dialog.dismiss();
            deleteFiles();
        });


        dialog.show();

    }

    /**
     * 删除
     * Delete file.
     */
    private void deleteFiles() {
        int selectCount = mPresenter.getSelectCount();
        if (selectCount <= 0) {
            BaseApplication.showSnackbar(getSwipeRefreshLayout(), "请选择文件");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.DialogTheme);
        builder.setMessage(String.format("确定删除选择的%d项？", selectCount))
                .setNegativeButton("不删", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setPositiveButton("删除", (dialog1, which) -> {
                    if (mPresenter.delete()) {
                        BaseApplication.showSnackbar(getSwipeRefreshLayout(), "已经删除");
//                mAdapter.removeData();
                        refresh();
                    }
                    if (mActionMode != null) {
                        mActionMode.finish();
                    }
                    dialog1.dismiss();
                }).show();


    }

    /**
     * 复制
     * Copt files.
     */
    private void coptFiles() {
        int selectCount = mPresenter.getSelectCount();
        if (selectCount <= 0) {
            BaseApplication.showSnackbar(getSwipeRefreshLayout(), "请选择文件");
            return;
        }

        mPresenter.copy();

    }

    /**
     * 剪切
     * Cut files.
     */
    private void cutFiles() {
        int selectCount = mPresenter.getSelectCount();
        if (selectCount <= 0) {
            BaseApplication.showSnackbar(getSwipeRefreshLayout(), "请选择文件");
            return;
        }
        mPresenter.cut();

    }

    @Override
    public boolean hasNeedEvent(int type) {
        //接受刷新数据
        return type == RxEvent.TYPE_REFRESH_FOLDER;
    }

    @Override
    public void onEventMainThread(RxEvent event) {
        if (event.isType(RxEvent.TYPE_REFRESH_FOLDER)) {
          //  mPresenter.refreshCurrentPath();
            mPresenter.closeEditMode();
            refresh();
        }
    }



}
