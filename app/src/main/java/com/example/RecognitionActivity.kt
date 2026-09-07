package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.vision.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

class RecognitionActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MyApplicationTheme{Surface(Modifier.fillMaxSize()){RecognitionScreen()}}}}
}

@Composable private fun RecognitionScreen(model:RecognitionViewModel=viewModel()){
    val activity=LocalContext.current as Activity
    var panel by remember { mutableIntStateOf(0) }
    var angle by remember { mutableFloatStateOf(0f) }
    var tilt by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var moving by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    var blank by remember { mutableStateOf(false) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){it?.let(model::select)}
    val photo=model.selectedPhoto
    LaunchedEffect(panel,angle,tilt,scale,offset,moving,blocked,blank,photo,model.panels,model.photoRevision,model.loading){
        if(model.panels.isEmpty()||model.loading)return@LaunchedEffect
        model.resetTracking()
        var tick=0
        do {
            val drift=if(moving)sin(tick*.09).toFloat() else 0f
            model.evaluate(panel,PanelPose(angle+drift*22,tilt,scale,(offset+drift*.35f).coerceIn(-1f,1f),blocked,blank),photo)
            tick++
            if(photo!=null)break
            delay(140)
        }while(isActive)
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("EcoSort · 면 인식",style=MaterialTheme.typography.titleLarge);TextButton(onClick={activity.finish()}){Text("닫기")}}
        Text(if(photo==null)"6면 움직임 실험 · 합성 입력" else "선택한 사진 · 기기 내 인식",fontWeight=FontWeight.Bold)
        Text("카메라·서버 전송 없이 사진의 특징점을 찾습니다. 등록된 두유 포장 디자인 한 종류만 지원합니다.",style=MaterialTheme.typography.bodySmall)
        model.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(model.panels.isEmpty()||model.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        model.frame?.let { frame ->
            Box(Modifier.fillMaxWidth().aspectRatio(frame.width.toFloat()/frame.height)){
                Image(frame.asImageBitmap(),"인식 입력 프레임",Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize()){
                    val match=model.detection?.match
                    if(match!=null){
                        val sx=size.width/frame.width;val sy=size.height/frame.height
                        val color=if(photo!=null||model.tracked.stable)Color(0xFF007F46) else Color(0xFFFFA000)
                        match.corners.forEachIndexed { i,p ->val next=match.corners[(i+1)%4];drawLine(color,Offset(p.x*sx,p.y*sy),Offset(next.x*sx,next.y*sy),3.dp.toPx()) }
                        match.points.forEach { p ->drawCircle(color,2.dp.toPx(),Offset(p.x*sx,p.y*sy)) }
                    }
                }
            }
        }
        val found=model.detection?.match
        val confirmed=found!=null&&(photo!=null||model.tracked.stable)
        Text(when{found==null->"등록 물품을 찾는 중";confirmed->"포장 디자인 일치 · ${found.label}";else->"일치 후보 확인 중 · ${model.tracked.consecutive}/3"},fontWeight=FontWeight.Bold)
        if(found!=null)Text("매일두유 고단백 검은콩 190mL · 등록 사진 기준",style=MaterialTheme.typography.bodySmall)
        model.detection?.let{Text("처리 ${it.elapsedMs}ms · 기하 검증점 ${found?.inliers?:0}개",style=MaterialTheme.typography.labelSmall)}
        Text("제품 이름·개봉 여부·내부 오염·재활용 가능성을 인증하는 결과는 아닙니다.",style=MaterialTheme.typography.bodySmall)
        if(photo==null){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(onClick={panel=(panel+5)%6},enabled=model.panels.isNotEmpty(),modifier=Modifier.weight(1f)){Text("이전 면")}
                OutlinedButton(onClick={panel=(panel+1)%6},enabled=model.panels.isNotEmpty(),modifier=Modifier.weight(1f)){Text("다음 면")}
            }
            Text("입력 면: ${model.panels.getOrNull(panel)?.label?:"준비 중"}",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                FilterChip(moving,{moving=!moving},{Text("자동 이동")})
                FilterChip(blocked,{blocked=!blocked},{Text("일부 가림")})
                FilterChip(blank,{blank=!blank},{Text("대상 숨김")})
            }
            Control("회전",angle,-40f..40f){angle=it}
            Control("기울임",tilt,-0.65f..0.65f){tilt=it}
            Control("크기",scale,.55f..1.15f){scale=it}
            Control("좌우 이동",offset,-1f..1f){offset=it}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("내 사진으로 인식")}
            if(photo!=null)OutlinedButton(onClick=model::synthetic){Text("6면 실험으로")}
        }
        if(photo!=null&&activity.intent.getBooleanExtra("allow_handoff",false)){
            Button(onClick={
                val uri=model.selectedUri?:return@Button
                activity.setResult(Activity.RESULT_OK,Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));activity.finish()
            },enabled=confirmed,modifier=Modifier.fillMaxWidth()){Text("이 사진으로 상태 확인")}
        } else if(photo!=null)Text("서버 안내는 로그인 후 사진 분석 화면에서 이어갈 수 있습니다.",style=MaterialTheme.typography.bodySmall)
        Text("이동·회전 화면은 등록 사진을 변형한 합성 입력입니다. 합성 인식 성공을 실물 정확도로 계산하지 않습니다. 다른 디자인과 심한 가림은 인식하지 못할 수 있습니다.",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Control(label:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit){
    Text(label,style=MaterialTheme.typography.labelMedium)
    Slider(value,onChange,valueRange=range,modifier=Modifier.fillMaxWidth())
}
