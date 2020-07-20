package com.xiaoshu.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageInfo;
import com.xiaoshu.config.util.ConfigUtil;
import com.xiaoshu.dao.DeptMapper;
import com.xiaoshu.entity.Dept;
import com.xiaoshu.entity.Emp;
import com.xiaoshu.entity.EmpVo;
import com.xiaoshu.entity.Operation;
import com.xiaoshu.entity.Role;
import com.xiaoshu.service.EmpService;
import com.xiaoshu.service.OperationService;
import com.xiaoshu.service.RoleService;
import com.xiaoshu.service.UserService;
import com.xiaoshu.util.StringUtil;
import com.xiaoshu.util.TimeUtil;
import com.xiaoshu.util.WriterUtil;

@Controller
@RequestMapping("emp")
public class EmpController extends LogController{
	static Logger logger = Logger.getLogger(EmpController.class);

	@Autowired
	private EmpService empService;
	@Autowired
	private UserService userService;
	
	
	@Autowired
	private RoleService roleService ;
	
	@Autowired
	private OperationService operationService;
	
	
	@RequestMapping("empIndex")
	public String index(HttpServletRequest request,Integer menuid) throws Exception{
		List<Role> roleList = roleService.findRole(new Role());
		List<Operation> operationList = operationService.findOperationIdsByMenuid(menuid);
		//查询员工部门
		List<Dept> dlist=empService.findAllDept();
		request.setAttribute("dlist", dlist);
		request.setAttribute("operationList", operationList);
		request.setAttribute("roleList", roleList);
		return "emp";
	}
	
	
	@RequestMapping(value="empList",method=RequestMethod.POST)
	public void userList(EmpVo empVo,HttpServletRequest request,HttpServletResponse response,String offset,String limit) throws Exception{
		try {
			String order = request.getParameter("order");
			String ordername = request.getParameter("ordername");
			
			Integer pageSize = StringUtil.isEmpty(limit)?ConfigUtil.getPageSize():Integer.parseInt(limit);
			Integer pageNum =  (Integer.parseInt(offset)/pageSize)+1;
			PageInfo<EmpVo> userList= empService.findUserPage(empVo,pageNum,pageSize,ordername,order);
			
			JSONObject jsonObj = new JSONObject();
			jsonObj.put("total",userList.getTotal() );
			jsonObj.put("rows", userList.getList());
	        WriterUtil.write(response,jsonObj.toString());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("用户展示错误",e);
			throw e;
		}
	}
	
	
	// 新增或修改
	@RequestMapping("reserveEmp")
	public void reserveUser(MultipartFile picFile,HttpServletRequest request,Emp emp,HttpServletResponse response) throws IllegalStateException, IOException{
		Integer eid = emp.getEid();
		JSONObject result=new JSONObject();
		//判断图片是否有值
		if (picFile!=null && picFile.getSize()>0) {
			//获取图片名称
			String filename = picFile.getOriginalFilename();
			//获取后缀名
			String suffix = filename.substring(filename.lastIndexOf("."));
			//重新命名
			String newFileName=System.currentTimeMillis()+suffix;
			//设置虚拟路径
			File file=new File("e:/img/"+newFileName);
			//上传图片
			picFile.transferTo(file);
			//将图片名称保存到数据库中
			emp.setPic(newFileName);
			
		}
		
		try {
			if (eid != null) {   // userId不为空 说明是修改
				Emp empName=empService.existUserWithUserName(emp.getEname());
				if(empName != null && empName.getEid().compareTo(eid)==0){
					emp.setEid(eid);
					empService.updateEmp(emp);
					result.put("success", true);
				}else{
					result.put("success", true);
					result.put("errorMsg", "该用户名被使用");
				}
				
			}else {   // 添加
				if(empService.existUserWithUserName(emp.getEname())==null){  // 没有重复可以添加
					empService.addEmp(emp);
					result.put("success", true);
				} else {
					result.put("success", true);
					result.put("errorMsg", "该用户名被使用");
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("保存用户信息错误",e);
			result.put("success", true);
			result.put("errorMsg", "对不起，操作失败");
		}
		WriterUtil.write(response, result.toString());
	}
	
	
	@RequestMapping("deleteEmp")
	public void delUser(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			String[] ids=request.getParameter("ids").split(",");
			for (String id : ids) {
				empService.deleteEmp(Integer.parseInt(id));
			}
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		WriterUtil.write(response, result.toString());
	}
	//报表查询
	@RequestMapping("getEcharts")
	public void getEcharts(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			//查询报表信息
			List<EmpVo> elist=empService.getEcharts();
			result.put("elist", elist);
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		WriterUtil.write(response, result.toString());
	}
	//导入
	@RequestMapping("importEmp")
	public void importEmp(MultipartFile excelFile,HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			//获取excel文件
			HSSFWorkbook wb=new HSSFWorkbook(excelFile.getInputStream());
			//获取文件中的sheet页
			HSSFSheet sheet = wb.getSheetAt(0);
			//获取最后一行行数
			int rowNum = sheet.getLastRowNum();
			//循环行数获取每一行对象
			for (int i = 1; i <= rowNum; i++) {
				//获取每一行中单元格
				HSSFRow row = sheet.getRow(i);
				String ename = row.getCell(0).getStringCellValue();
				Double numericCellValue = row.getCell(1).getNumericCellValue();
				int age = numericCellValue.intValue();
				Date date = row.getCell(2).getDateCellValue();
				String gender = row.getCell(3).getStringCellValue();
				String pic = row.getCell(4).getStringCellValue();
				String dname = row.getCell(5).getStringCellValue();
				//根据部门名称查询部门ID
				Integer depid=findDepIdByDname(dname);
				//封装emp对象
				Emp emp=new Emp();
				emp.setEname(ename);
				emp.setAge(age);
				emp.setBirthday(date);
				emp.setGender(gender);
				emp.setPic(pic);
				emp.setDepid(depid);
				//调用service保存方法保存数据
			empService.addEmp(emp);
			}
			
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		WriterUtil.write(response, result.toString());
	}
	@Autowired
	private DeptMapper deptMapper;
	//假如导入数据的时候导入的数据库中不存在
	private Integer findDepIdByDname(String dname) {
		Dept dept= new Dept();
		dept.setDname(dname);
		Dept one = deptMapper.selectOne(dept);
		return one.getDepid();
	}


	//导出到指定位置
	@RequestMapping("exportEmp")
	public void exportEmp(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			//创建Excel文档
			HSSFWorkbook wb=new HSSFWorkbook();
			//创建sheet页
			HSSFSheet sheet=wb.createSheet();
			//声明表头信息
			String[] header={"员工编号","员工姓名","员工年龄","员工生日","员工性别","员工头像","员工部门"};
			//创建行
			HSSFRow row=sheet.createRow(0);
			for (int i = 0; i < header.length; i++) {
				//创建单元格
				HSSFCell cell=row.createCell(i);
				cell.setCellValue(header[i]);
			}
         	//从数据库中将所有数据读出
			List<EmpVo> list = empService.findPage(new EmpVo());
			//将数据依次遍历放到文档中
			for (int i = 0; i <list.size(); i++) {
				//将数据放入sheet单元格中
				HSSFRow row2 = sheet.createRow(i+1);
				EmpVo empVo = list.get(i);
				if (empVo.getGender().equals("男")) {
					continue;
				}
				if (empVo.getDepid()!=2) {
					continue;
				}
				//将对象中的值放入单元格
				row2.createCell(0).setCellValue(empVo.getEid());
				row2.createCell(1).setCellValue(empVo.getEname());
				row2.createCell(2).setCellValue(empVo.getAge());
				row2.createCell(3).setCellValue(TimeUtil.formatTime(empVo.getBirthday(), "yyyy-MM-dd"));
				row2.createCell(4).setCellValue(empVo.getGender());
				row2.createCell(5).setCellValue(empVo.getPic());
				row2.createCell(6).setCellValue(empVo.getDname());
			}
			//导出
			OutputStream os;
			//设置导出路径
			File file = new File("e:/img/员工管理.xls");
			
			if (!file.exists()){//若此目录不存在，则创建之  
				file.createNewFile();  
				logger.debug("创建文件夹路径为："+ file.getPath());  
            } 
			os = new FileOutputStream(file);
			wb.write(os);
			os.close();
			
			
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
		WriterUtil.write(response, result.toString());
	}
/*	@RequestMapping("exportEmp")
	public void exportEmp(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		try {
			//创建Excel文档
			HSSFWorkbook wb=new HSSFWorkbook();
			//创建sheet页
			HSSFSheet sheet=wb.createSheet();
			//声明表头信息
			String[] header={"员工编号","员工姓名","员工年龄","员工生日","员工性别","员工头像","员工部门"};
			//创建行
			HSSFRow row=sheet.createRow(0);
			for (int i = 0; i < header.length; i++) {
				//创建单元格
				HSSFCell cell=row.createCell(i);
				cell.setCellValue(header[i]);
			}
         	//从数据库中将所有数据读出
			List<EmpVo> list = empService.findPage(new EmpVo());
			//将数据依次遍历放到文档中
			for (int i = 0; i <list.size(); i++) {
				//将数据放入sheet单元格中
				HSSFRow row2 = sheet.createRow(i+1);
				EmpVo empVo = list.get(i);
				if (empVo.getGender().equals("男")) {
					continue;
				}
				if (empVo.getDepid()!=2) {
					continue;
				}
				//将对象中的值放入单元格
				row2.createCell(0).setCellValue(empVo.getEid());
				row2.createCell(1).setCellValue(empVo.getEname());
				row2.createCell(2).setCellValue(empVo.getAge());
				row2.createCell(3).setCellValue(TimeUtil.formatTime(empVo.getBirthday(), "yyyy-MM-dd"));
				row2.createCell(4).setCellValue(empVo.getGender());
				row2.createCell(5).setCellValue(empVo.getPic());
				row2.createCell(6).setCellValue(empVo.getDname());
			}
			response.setHeader("Content-Disposition", "attachment;filename="+URLEncoder.encode("员工列表.xls", "UTF-8"));
			response.setHeader("Connection", "close");
			response.setHeader("Content-Type", "application/octet-stream");
	        wb.write(response.getOutputStream());
			wb.close();
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("删除用户信息错误",e);
			result.put("errorMsg", "对不起，删除失败");
		}
	}*/
	
	
/*
 * 复制logController
 * 	*//**
	 * 备份
	 *//*
	@RequestMapping("exportEmp")
	public void backup(HttpServletRequest request,HttpServletResponse response){
		JSONObject result = new JSONObject();
		try {
			String time = TimeUtil.formatTime(new Date(), "yyyyMMddHHmmss");
		    String excelName = "手动备份"+time;
	List<EmpVo> list = empService.findPage(new EmpVo());
	String[] headers={"员工编号","员工姓名","员工年龄","员工生日","员工性别","员工头像","员工部门"};
			// 1导入硬盘
			ExportExcelToDisk(request,headers,list, excelName);
			result.put("success", true);
		} catch (Exception e) {
			e.printStackTrace();
			result.put("", "对不起，备份失败");
		}
		WriterUtil.write(response, result.toString());
	}
	
	
	
	// 导出到硬盘
	@SuppressWarnings("resource")
	private void ExportExcelToDisk(HttpServletRequest request,
			String[] handers, List<EmpVo> list, String excleName) throws Exception {
		
		try {
			HSSFWorkbook wb = new HSSFWorkbook();//创建工作簿
			HSSFSheet sheet = wb.createSheet("操作记录备份");//第一个sheet
			HSSFRow rowFirst = sheet.createRow(0);//第一个sheet第一行为标题
			rowFirst.setHeight((short) 500);
			for (int i = 0; i < handers.length; i++) {
				sheet.setColumnWidth((short) i, (short) 4000);// 设置列宽
			}
			//写标题了
			for (int i = 0; i < handers.length; i++) {
			    //获取第一行的每一个单元格
			    HSSFCell cell = rowFirst.createCell(i);
			    //往单元格里面写入值
			    cell.setCellValue(handers[i]);
			}
			for (int i = 0;i < list.size(); i++) {
			    //获取list里面存在是数据集对象
		        EmpVo empVo = list.get(i);
			    //创建数据行
			    HSSFRow row2 = sheet.createRow(i+1);
			    //设置对应单元格的值
			    row2.setHeight((short)400);   // 设置每行的高度
			    //"序号","操作人","IP地址","操作时间","操作模块","操作类型","详情"
			    row2.createCell(0).setCellValue(i+1);
			  //将对象中的值放入单元格
				row2.createCell(0).setCellValue(empVo.getEid());
				row2.createCell(1).setCellValue(empVo.getEname());
				row2.createCell(2).setCellValue(empVo.getAge());
				row2.createCell(3).setCellValue(TimeUtil.formatTime(empVo.getBirthday(), "yyyy-MM-dd"));
				row2.createCell(4).setCellValue(empVo.getGender());
				row2.createCell(5).setCellValue(empVo.getPic());
				row2.createCell(6).setCellValue(empVo.getDname());
			}
			//写出文件（path为文件路径含文件名）
				OutputStream os;
				File file = new File("e:/img/"+excleName+".xls");
				
				if (!file.exists()){//若此目录不存在，则创建之  
					file.createNewFile();  
					logger.debug("创建文件夹路径为："+ file.getPath());  
	            } 
				os = new FileOutputStream(file);
				wb.write(os);
				os.close();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
	}*/

/*	@RequestMapping("editPassword")
	public void editPassword(HttpServletRequest request,HttpServletResponse response){
		JSONObject result=new JSONObject();
		String oldpassword = request.getParameter("oldpassword");
		String newpassword = request.getParameter("newpassword");
		HttpSession session = request.getSession();
		User currentUser = (User) session.getAttribute("currentUser");
		if(currentUser.getPassword().equals(oldpassword)){
			User user = new User();
			user.setUserid(currentUser.getUserid());
			user.setPassword(newpassword);
			try {
				userService.updateUser(user);
				currentUser.setPassword(newpassword);
				session.removeAttribute("currentUser"); 
				session.setAttribute("currentUser", currentUser);
				result.put("success", true);
			} catch (Exception e) {
				e.printStackTrace();
				logger.error("修改密码错误",e);
				result.put("errorMsg", "对不起，修改密码失败");
			}
		}else{
			logger.error(currentUser.getUsername()+"修改密码时原密码输入错误！");
			result.put("errorMsg", "对不起，原密码输入错误！");
		}
		WriterUtil.write(response, result.toString());
	}*/
}
