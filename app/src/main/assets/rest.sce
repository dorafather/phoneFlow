상태::FLOW.초기
{
  FLOW.수신메시지.이벤트명 == 채팅입력    처리.채팅입력처리
  FLOW.수신메시지.이벤트명 == 시작    처리.procRestInit
  FLOW.수신메시지.이벤트명 == 주식감시틱    처리.주식관심종목감시처리
  KRX.수신메시지.응답코드 == 200    처리.KRX응답분기처리
  FLOW.수신메시지.이벤트명 == 기상알림틱    처리.기상알림확인처리
  KMA.수신메시지.응답코드 == 200    처리.KMA응답분기처리
  FLOW.수신메시지.이벤트명 == 미세먼지알림틱    처리.미세먼지알림확인처리
  KECO.수신메시지.응답코드 == 200    처리.KECO응답분기처리
  FLOW.수신메시지.이벤트명 == 공휴일알림틱    처리.공휴일알림확인처리
  KMA_SPCD.수신메시지.응답코드 == 200    처리.KMASPCD응답분기처리
  MOLIT.수신메시지.응답코드 == 200    처리.MOLIT응답분기처리
}
처리::FLOW.채팅입력처리
{
  만약에(참)
    함수.저장(chat_input_text,수신메시지.text)
    함수.앞자리비교(cmd_help,세션.chat_input_text,help)
    함수.앞자리비교(cmd_주식,세션.chat_input_text,주식)
    함수.앞자리비교(cmd_기상청,세션.chat_input_text,기상청)
    함수.앞자리비교(cmd_미세먼지,세션.chat_input_text,미세먼지)
    함수.앞자리비교(cmd_공휴일,세션.chat_input_text,공휴일)
    함수.앞자리비교(cmd_실거래가,세션.chat_input_text,실거래가)
    처리.채팅명령분기
}
처리::FLOW.채팅명령분기
{
  만약에(세션.cmd_help == 1)
    전송.도움말응답
  그외그외(세션.cmd_주식 == 1)
    처리.텔레그램주식명령처리
  그외그외(세션.cmd_기상청 == 1)
    처리.텔레그램기상청명령처리
  그외그외(세션.cmd_미세먼지 == 1)
    처리.텔레그램미세먼지명령처리
  그외그외(세션.cmd_공휴일 == 1)
    처리.텔레그램공휴일명령처리
  그외그외(세션.cmd_실거래가 == 1)
    처리.텔레그램실거래가명령처리
  그외
    전송.명령모름응답
}
전송::FLOW.도움말응답
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 문장.도움말문장
}
전송::FLOW.명령모름응답
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 문장.명령모름안내문장
}
문장::FLOW.명령모름안내문장
{이해하지 못한 명령입니다. "help"라고 입력하면 전체 명령 목록을 볼 수 있습니다.}
문장::FLOW.도움말문장
{[사용 가능한 명령어]
주식 관심종목 조회 - 등록된 관심종목 시세 조회
주식 관심종목 추가 [종목명] - 관심종목 등록(최대 5개)
주식 관심종목 삭제 [종목명] - 관심종목 해제
기상청 날씨 [지역] - 단기예보 조회(지역 생략 시 기본 지역)
기상청 지역 추가 [지역] - 관심지역 등록(최대 5개)
기상청 지역 삭제 [지역] - 관심지역 해제
기상청 지역 조회 - 등록된 관심지역 전체 날씨 조회
미세먼지 [지역] - 대기질 조회(지역 생략 시 기본 지역)
미세먼지 지역 추가 [지역] - 관심지역 등록(최대 5개)
미세먼지 지역 삭제 [지역] - 관심지역 해제
미세먼지 지역 조회 - 등록된 관심지역 전체 대기질 조회
공휴일 - 이번 달 공휴일 조회
공휴일 [월] - 해당 월 공휴일 조회
실거래가 - 이번 달 기본 지역 아파트 실거래가 조회
실거래가 [계약년월] - 해당 월 기본 지역 실거래가 조회
실거래가 [지역명] [계약년월] - 해당 지역/월 실거래가 조회
실거래가 지역추가 [지역명] - 관심지역 등록(최대 5개, 서울 25개구)
실거래가 지역삭제 [지역명] - 관심지역 해제
실거래가 지역조회 - 등록된 관심지역 목록 조회
help - 이 도움말 표시}
처리::FLOW.procRestInit
{
  만약에(참)
    처리.주식관심종목초기화
    타이머.주식감시타이머
    처리.기상청관심지역초기화
    타이머.기상알림타이머
    처리.미세먼지관심지역초기화
    타이머.미세먼지알림타이머
    타이머.공휴일알림타이머
    처리.MOLIT관심지역초기화
}
처리::FLOW.주식관심종목초기화
{
  만약에(참)
    함수.저장(krx_watch_csv,없음)
    함수.저장(krx_seed_found,0)
    함수.저장(krx_ini_category,KRX_WATCHLIST)
    함수.저장(krx_ini_clear_value,없음)
    함수.저장(krx_ini_pipe,|)
    함수.저장(krx_ini_colon,:)
    처리.주식시드확인1
}
처리::FLOW.주식시드확인1
{
  만약에(설정.KRX_WATCHLIST.종목1 != NULL) 그리고(설정.KRX_WATCHLIST.종목1 != 없음) 그리고(세션.krx_seed_found != 1)
    함수.저장(krx_watch_csv,설정.KRX_WATCHLIST.종목1)
    함수.저장(krx_seed_found,1)
    처리.주식시드확인2
  그외그외(설정.KRX_WATCHLIST.종목1 != NULL) 그리고(설정.KRX_WATCHLIST.종목1 != 없음)
    함수.붙이기(krx_watch_csv,|,설정.KRX_WATCHLIST.종목1)
    처리.주식시드확인2
  그외
    처리.주식시드확인2
}
처리::FLOW.주식시드확인2
{
  만약에(설정.KRX_WATCHLIST.종목2 != NULL) 그리고(설정.KRX_WATCHLIST.종목2 != 없음) 그리고(세션.krx_seed_found != 1)
    함수.저장(krx_watch_csv,설정.KRX_WATCHLIST.종목2)
    함수.저장(krx_seed_found,1)
    처리.주식시드확인3
  그외그외(설정.KRX_WATCHLIST.종목2 != NULL) 그리고(설정.KRX_WATCHLIST.종목2 != 없음)
    함수.붙이기(krx_watch_csv,|,설정.KRX_WATCHLIST.종목2)
    처리.주식시드확인3
  그외
    처리.주식시드확인3
}
처리::FLOW.주식시드확인3
{
  만약에(설정.KRX_WATCHLIST.종목3 != NULL) 그리고(설정.KRX_WATCHLIST.종목3 != 없음) 그리고(세션.krx_seed_found != 1)
    함수.저장(krx_watch_csv,설정.KRX_WATCHLIST.종목3)
    함수.저장(krx_seed_found,1)
    처리.주식시드확인4
  그외그외(설정.KRX_WATCHLIST.종목3 != NULL) 그리고(설정.KRX_WATCHLIST.종목3 != 없음)
    함수.붙이기(krx_watch_csv,|,설정.KRX_WATCHLIST.종목3)
    처리.주식시드확인4
  그외
    처리.주식시드확인4
}
처리::FLOW.주식시드확인4
{
  만약에(설정.KRX_WATCHLIST.종목4 != NULL) 그리고(설정.KRX_WATCHLIST.종목4 != 없음) 그리고(세션.krx_seed_found != 1)
    함수.저장(krx_watch_csv,설정.KRX_WATCHLIST.종목4)
    함수.저장(krx_seed_found,1)
    처리.주식시드확인5
  그외그외(설정.KRX_WATCHLIST.종목4 != NULL) 그리고(설정.KRX_WATCHLIST.종목4 != 없음)
    함수.붙이기(krx_watch_csv,|,설정.KRX_WATCHLIST.종목4)
    처리.주식시드확인5
  그외
    처리.주식시드확인5
}
처리::FLOW.주식시드확인5
{
  만약에(설정.KRX_WATCHLIST.종목5 != NULL) 그리고(설정.KRX_WATCHLIST.종목5 != 없음) 그리고(세션.krx_seed_found != 1)
    함수.저장(krx_watch_csv,설정.KRX_WATCHLIST.종목5)
    함수.저장(krx_seed_found,1)
    로그.출력(주식 관심종목 시드 로딩 완료)
  그외그외(설정.KRX_WATCHLIST.종목5 != NULL) 그리고(설정.KRX_WATCHLIST.종목5 != 없음)
    함수.붙이기(krx_watch_csv,|,설정.KRX_WATCHLIST.종목5)
    로그.출력(주식 관심종목 시드 로딩 완료)
  그외
    로그.출력(주식 관심종목 시드 로딩 완료)
}
처리::FLOW.주식관심종목감시처리
{
  만약에(참)
    함수.저장(krx_mode,폴링단건)
    타이머.주식감시타이머
    처리.주식관심종목순회시작
}
처리::TELEGRAM.텔레그램주식명령처리
{
  만약에(참)
    함수.단어분리(cmd_word_list,세션.chat_input_text)
    함수.단어합치기(cmd_rest,세션.리스트.cmd_word_list,1)
    함수.앞자리비교(cmd_관심종목,세션.cmd_rest,관심종목)
    처리.텔레그램주식명령분기
}
처리::TELEGRAM.텔레그램주식명령분기
{
  만약에(세션.cmd_관심종목 == 1)
    처리.텔레그램주식관심종목명령처리
  그외
    전송.명령모름응답
}
처리::TELEGRAM.텔레그램주식관심종목명령처리
{
  만약에(참)
    함수.단어분리(cmd_word_list2,세션.cmd_rest)
    함수.단어합치기(cmd_rest2,세션.리스트.cmd_word_list2,1)
    함수.앞자리비교(cmd_추가,세션.cmd_rest2,추가)
    함수.앞자리비교(cmd_삭제,세션.cmd_rest2,삭제)
    함수.앞자리비교(cmd_조회,세션.cmd_rest2,조회)
    처리.텔레그램주식관심종목명령분기
}
처리::TELEGRAM.텔레그램주식관심종목명령분기
{
  만약에(세션.cmd_추가 == 1)
    처리.텔레그램주식관심종목추가명령처리
  그외그외(세션.cmd_삭제 == 1)
    처리.텔레그램주식관심종목삭제명령처리
  그외그외(세션.cmd_조회 == 1)
    처리.텔레그램주식관심종목조회명령처리
  그외
    전송.명령모름응답
}
처리::TELEGRAM.텔레그램주식관심종목추가명령처리
{
  만약에(참)
    함수.단어분리(cmd_word_list3,세션.cmd_rest2)
    함수.단어합치기(krx_target_name,세션.리스트.cmd_word_list3,1)
    함수.저장(krx_mode,검색)
    전송.KRX종목검색전송
}
처리::TELEGRAM.텔레그램주식관심종목삭제명령처리
{
  만약에(참)
    함수.단어분리(cmd_word_list4,세션.cmd_rest2)
    함수.단어합치기(krx_target_name,세션.리스트.cmd_word_list4,1)
    함수.저장(krx_del_match_prefix,세션.krx_target_name)
    함수.붙이기(krx_del_match_prefix,:)
    함수.쪼개기(krx_watch_list,세션.krx_watch_csv,|)
    함수.저장(krx_del_idx,0)
    함수.저장(krx_del_found,0)
    함수.저장(krx_new_csv,없음)
    함수.저장(krx_new_found,0)
    처리.주식관심종목삭제순회
}
처리::KRX.주식관심종목삭제순회
{
  만약에(세션.krx_del_idx >= 세션.리스트.krx_watch_list.SIZE)
    처리.주식관심종목삭제완료
  그외
    처리.주식관심종목삭제항목검사
}
처리::KRX.주식관심종목삭제항목검사
{
  만약에(세션.리스트.krx_watch_list[세션.krx_del_idx] === 세션.krx_del_match_prefix)
    함수.더하기(krx_del_found,세션.krx_del_found,1)
    함수.더하기(krx_del_idx,세션.krx_del_idx,1)
    처리.주식관심종목삭제순회
  그외
    처리.주식관심종목삭제보존
}
처리::KRX.주식관심종목삭제보존
{
  만약에(세션.krx_new_found == 0)
    함수.저장(krx_new_csv,세션.리스트.krx_watch_list[세션.krx_del_idx])
    함수.저장(krx_new_found,1)
    함수.더하기(krx_del_idx,세션.krx_del_idx,1)
    처리.주식관심종목삭제순회
  그외
    함수.붙이기(krx_new_csv,세션.krx_ini_pipe,세션.리스트.krx_watch_list[세션.krx_del_idx])
    함수.더하기(krx_del_idx,세션.krx_del_idx,1)
    처리.주식관심종목삭제순회
}
처리::KRX.주식관심종목삭제완료
{
  만약에(세션.krx_del_found > 0)
    함수.저장(krx_watch_csv,세션.krx_new_csv)
    함수.저장(krx_ini_del_found,0)
    처리.주식관심종목ini삭제찾기1
  그외
    함수.저장(krx_reply_text,문장.주식관심종목삭제실패문장)
    전송.주식관심종목응답전송
}
처리::KRX.주식관심종목ini삭제찾기1
{
  만약에(설정.KRX_WATCHLIST.종목1 === 세션.krx_del_match_prefix) 그리고(세션.krx_ini_del_found != 1)
    함수.저장(krx_ini_slot_key,종목1)
    함수.설정저장(세션.krx_ini_category,세션.krx_ini_slot_key,세션.krx_ini_clear_value)
    함수.저장(krx_ini_del_found,1)
    처리.주식관심종목ini삭제찾기2
  그외
    처리.주식관심종목ini삭제찾기2
}
처리::KRX.주식관심종목ini삭제찾기2
{
  만약에(설정.KRX_WATCHLIST.종목2 === 세션.krx_del_match_prefix) 그리고(세션.krx_ini_del_found != 1)
    함수.저장(krx_ini_slot_key,종목2)
    함수.설정저장(세션.krx_ini_category,세션.krx_ini_slot_key,세션.krx_ini_clear_value)
    함수.저장(krx_ini_del_found,1)
    처리.주식관심종목ini삭제찾기3
  그외
    처리.주식관심종목ini삭제찾기3
}
처리::KRX.주식관심종목ini삭제찾기3
{
  만약에(설정.KRX_WATCHLIST.종목3 === 세션.krx_del_match_prefix) 그리고(세션.krx_ini_del_found != 1)
    함수.저장(krx_ini_slot_key,종목3)
    함수.설정저장(세션.krx_ini_category,세션.krx_ini_slot_key,세션.krx_ini_clear_value)
    함수.저장(krx_ini_del_found,1)
    처리.주식관심종목ini삭제찾기4
  그외
    처리.주식관심종목ini삭제찾기4
}
처리::KRX.주식관심종목ini삭제찾기4
{
  만약에(설정.KRX_WATCHLIST.종목4 === 세션.krx_del_match_prefix) 그리고(세션.krx_ini_del_found != 1)
    함수.저장(krx_ini_slot_key,종목4)
    함수.설정저장(세션.krx_ini_category,세션.krx_ini_slot_key,세션.krx_ini_clear_value)
    함수.저장(krx_ini_del_found,1)
    처리.주식관심종목ini삭제찾기5
  그외
    처리.주식관심종목ini삭제찾기5
}
처리::KRX.주식관심종목ini삭제찾기5
{
  만약에(설정.KRX_WATCHLIST.종목5 === 세션.krx_del_match_prefix) 그리고(세션.krx_ini_del_found != 1)
    함수.저장(krx_ini_slot_key,종목5)
    함수.설정저장(세션.krx_ini_category,세션.krx_ini_slot_key,세션.krx_ini_clear_value)
    처리.주식관심종목삭제응답
  그외
    처리.주식관심종목삭제응답
}
처리::KRX.주식관심종목삭제응답
{
  만약에(참)
    함수.저장(krx_reply_text,문장.주식관심종목삭제완료문장)
    전송.주식관심종목응답전송
}
처리::TELEGRAM.텔레그램주식관심종목조회명령처리
{
  만약에(참)
    함수.저장(krx_mode,조회단건)
    처리.주식관심종목순회시작
}
처리::KRX.주식관심종목순회시작
{
  만약에(세션.krx_watch_csv == 없음)
    함수.저장(krx_walk_lines,없음)
    처리.주식관심종목순회완료
  그외
    함수.쪼개기(krx_watch_list,세션.krx_watch_csv,|)
    함수.저장(krx_walk_idx,0)
    함수.저장(krx_walk_found,0)
    함수.저장(krx_walk_lines,없음)
    처리.주식관심종목순회다음
}
처리::KRX.주식관심종목순회다음
{
  만약에(세션.krx_walk_idx >= 세션.리스트.krx_watch_list.SIZE)
    처리.주식관심종목순회완료
  그외
    함수.쪼개기(krx_item_parts,세션.리스트.krx_watch_list[세션.krx_walk_idx],:)
    함수.저장(krx_walk_name,세션.리스트.krx_item_parts[0])
    함수.저장(krx_walk_code,세션.리스트.krx_item_parts[1])
    전송.KRX시세조회전송
}
처리::KRX.주식관심종목순회완료
{
  만약에(세션.krx_mode == 조회단건) 그리고(세션.krx_walk_lines != 없음)
    함수.저장(krx_reply_text,세션.krx_walk_lines)
    전송.주식관심종목응답전송
  그외그외(세션.krx_mode == 조회단건)
    함수.저장(krx_reply_text,문장.주식관심종목조회빈목록문장)
    전송.주식관심종목응답전송
  그외그외(세션.krx_mode == 폴링단건) 그리고(세션.krx_walk_lines != 없음)
    함수.저장(krx_watch_alert_text,세션.krx_walk_lines)
    전송.주식감시알림텔레그램전송
  그외
    로그.출력(주식 관심종목 감시 - 임계값 초과 종목 없음, 알림 생략)
}
처리::KRX.KRX응답분기처리
{
  만약에(세션.krx_mode == 검색)
    함수.객체저장(krx_search_items,수신메시지.response.body.items.item)
    함수.저장(krx_search_idx,0)
    처리.주식종목검색순회
  그외그외(세션.krx_mode == 조회단건)
    함수.객체저장(krx_price_items,수신메시지.response.body.items.item)
    처리.주식관심종목조회단건처리
  그외그외(세션.krx_mode == 폴링단건)
    함수.객체저장(krx_price_items,수신메시지.response.body.items.item)
    처리.주식감시단건처리
  그외
    로그.출력(KRX 알 수 없는 응답 모드)
}
처리::KRX.주식종목검색순회
{
  만약에(세션.객체.krx_search_items[세션.krx_search_idx].itmsNm == NULL)
    처리.주식종목검색실패
  그외그외(세션.객체.krx_search_items[세션.krx_search_idx].itmsNm == 세션.krx_target_name)
    함수.저장(krx_resolved_code,세션.객체.krx_search_items[세션.krx_search_idx].srtnCd)
    처리.주식종목검색성공
  그외
    함수.더하기(krx_search_idx,세션.krx_search_idx,1)
    처리.주식종목검색순회
}
처리::KRX.주식종목검색성공
{
  만약에(참)
    함수.저장(krx_ini_write_value,세션.krx_target_name)
    함수.붙이기(krx_ini_write_value,세션.krx_ini_colon,세션.krx_resolved_code)
    함수.저장(krx_ini_found,0)
    처리.주식관심종목ini빈슬롯찾기1
}
처리::KRX.주식관심종목ini빈슬롯찾기1
{
  만약에(설정.KRX_WATCHLIST.종목1 == NULL)
    함수.저장(krx_ini_slot_key,종목1)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외그외(설정.KRX_WATCHLIST.종목1 == 없음)
    함수.저장(krx_ini_slot_key,종목1)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외
    처리.주식관심종목ini빈슬롯찾기2
}
처리::KRX.주식관심종목ini빈슬롯찾기2
{
  만약에(설정.KRX_WATCHLIST.종목2 == NULL)
    함수.저장(krx_ini_slot_key,종목2)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외그외(설정.KRX_WATCHLIST.종목2 == 없음)
    함수.저장(krx_ini_slot_key,종목2)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외
    처리.주식관심종목ini빈슬롯찾기3
}
처리::KRX.주식관심종목ini빈슬롯찾기3
{
  만약에(설정.KRX_WATCHLIST.종목3 == NULL)
    함수.저장(krx_ini_slot_key,종목3)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외그외(설정.KRX_WATCHLIST.종목3 == 없음)
    함수.저장(krx_ini_slot_key,종목3)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외
    처리.주식관심종목ini빈슬롯찾기4
}
처리::KRX.주식관심종목ini빈슬롯찾기4
{
  만약에(설정.KRX_WATCHLIST.종목4 == NULL)
    함수.저장(krx_ini_slot_key,종목4)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외그외(설정.KRX_WATCHLIST.종목4 == 없음)
    함수.저장(krx_ini_slot_key,종목4)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외
    처리.주식관심종목ini빈슬롯찾기5
}
처리::KRX.주식관심종목ini빈슬롯찾기5
{
  만약에(설정.KRX_WATCHLIST.종목5 == NULL)
    함수.저장(krx_ini_slot_key,종목5)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외그외(설정.KRX_WATCHLIST.종목5 == 없음)
    함수.저장(krx_ini_slot_key,종목5)
    함수.저장(krx_ini_found,1)
    처리.주식관심종목ini쓰기
  그외
    처리.주식관심종목ini쓰기
}
처리::KRX.주식관심종목ini쓰기
{
  만약에(세션.krx_ini_found == 1)
    함수.설정저장(세션.krx_ini_category,세션.krx_ini_slot_key,세션.krx_ini_write_value)
    처리.주식관심종목추가세션갱신
  그외
    함수.저장(krx_reply_text,문장.주식관심종목추가한도초과문장)
    전송.주식관심종목응답전송
}
처리::KRX.주식관심종목추가세션갱신
{
  만약에(세션.krx_watch_csv == 없음)
    함수.저장(krx_watch_csv,세션.krx_ini_write_value)
    함수.저장(krx_reply_text,문장.주식관심종목추가완료문장)
    전송.주식관심종목응답전송
  그외
    함수.붙이기(krx_watch_csv,세션.krx_ini_pipe,세션.krx_ini_write_value)
    함수.저장(krx_reply_text,문장.주식관심종목추가완료문장)
    전송.주식관심종목응답전송
}
처리::KRX.주식종목검색실패
{
  만약에(참)
    함수.저장(krx_reply_text,문장.주식관심종목검색실패문장)
    전송.주식관심종목응답전송
}
처리::KRX.주식관심종목조회단건처리
{
  만약에(세션.객체.krx_price_items[0].clpr == NULL)
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
  그외
    함수.저장(krx_line_name,세션.객체.krx_price_items[0].itmsNm)
    함수.저장(krx_line_price,세션.객체.krx_price_items[0].clpr)
    함수.저장(krx_line_rate,세션.객체.krx_price_items[0].fltRt)
    처리.주식관심종목조회라인추가
}
처리::KRX.주식관심종목조회라인추가
{
  만약에(세션.krx_walk_found == 0)
    함수.저장(krx_walk_lines,문장.KRX주식시세라인문장)
    함수.저장(krx_walk_found,1)
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
  그외
    함수.붙이기(krx_walk_lines,|,문장.KRX주식시세라인문장)
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
}
처리::KRX.주식감시단건처리
{
  만약에(세션.객체.krx_price_items[0].fltRt == NULL)
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
  그외
    함수.저장(krx_line_name,세션.객체.krx_price_items[0].itmsNm)
    함수.저장(krx_line_price,세션.객체.krx_price_items[0].clpr)
    함수.저장(krx_line_rate,세션.객체.krx_price_items[0].fltRt)
    함수.부분비교(krx_is_neg,세션.krx_line_rate,-)
    처리.주식감시부호분기
}
처리::KRX.주식감시부호분기
{
  만약에(세션.krx_is_neg == 1)
    함수.추출(krx_rate_abs,세션.krx_line_rate,1,10)
    처리.주식감시임계값비교
  그외
    함수.저장(krx_rate_abs,세션.krx_line_rate)
    처리.주식감시임계값비교
}
처리::KRX.주식감시임계값비교
{
  만약에(세션.krx_rate_abs >= 설정.KRX.threshold)
    처리.주식감시라인추가
  그외
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
}
처리::KRX.주식감시라인추가
{
  만약에(세션.krx_walk_found == 0)
    함수.저장(krx_walk_lines,문장.KRX주식감시알림라인문장)
    함수.저장(krx_walk_found,1)
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
  그외
    함수.붙이기(krx_walk_lines,|,문장.KRX주식감시알림라인문장)
    함수.더하기(krx_walk_idx,세션.krx_walk_idx,1)
    처리.주식관심종목순회다음
}
전송::KRX.KRX종목검색전송
{
  전송메시지.메소드 = GET
  전송메시지.주소.도메인 = 설정.KRX.domain
  전송메시지.주소.경로 = /getStockPriceInfo_V2
  전송메시지.주소.파라미터[0].key = serviceKey
  전송메시지.주소.파라미터[0].val = 설정.K_DATA.service_key
  전송메시지.주소.파라미터[1].key = resultType
  전송메시지.주소.파라미터[1].val = json
  전송메시지.주소.파라미터[2].key = numOfRows
  전송메시지.주소.파라미터[2].val = 20
  전송메시지.주소.파라미터[3].key = pageNo
  전송메시지.주소.파라미터[3].val = 1
  전송메시지.주소.파라미터[4].key = likeItmsNm
  전송메시지.주소.파라미터[4].val = 세션.krx_target_name
}
전송::KRX.KRX시세조회전송
{
  전송메시지.메소드 = GET
  전송메시지.주소.도메인 = 설정.KRX.domain
  전송메시지.주소.경로 = /getStockPriceInfo_V2
  전송메시지.주소.파라미터[0].key = serviceKey
  전송메시지.주소.파라미터[0].val = 설정.K_DATA.service_key
  전송메시지.주소.파라미터[1].key = resultType
  전송메시지.주소.파라미터[1].val = json
  전송메시지.주소.파라미터[2].key = numOfRows
  전송메시지.주소.파라미터[2].val = 1
  전송메시지.주소.파라미터[3].key = pageNo
  전송메시지.주소.파라미터[3].val = 1
  전송메시지.주소.파라미터[4].key = likeSrtnCd
  전송메시지.주소.파라미터[4].val = 세션.krx_walk_code
}
전송::FLOW.주식관심종목응답전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 세션.krx_reply_text
}
전송::FLOW.주식감시알림텔레그램전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 문장.주식감시알림문장
}
타이머::KRX.주식감시타이머
{
  전송메시지.이벤트명 = 주식감시틱
  전송메시지.시간 = 86400000
}
문장::KRX.KRX주식시세라인문장
{$$$세션.krx_line_name$$$ $$$세션.krx_line_price$$$원 $$$세션.krx_line_rate$$$% (전일 기준)}
문장::KRX.KRX주식감시알림라인문장
{$$$세션.krx_line_name$$$ $$$세션.krx_line_rate$$$% $$$세션.krx_line_price$$$원 (전일 기준)}
문장::TELEGRAM.주식감시알림문장
{[주식 관심종목 알림] 전일 대비 등락률 $$$설정.KRX.threshold$$$% 이상 변동된 종목이 있습니다 (전일 기준).
$$$세션.krx_watch_alert_text$$$}
문장::TELEGRAM.주식관심종목추가완료문장
{$$$세션.krx_target_name$$$($$$세션.krx_resolved_code$$$)를 관심종목에 추가했습니다. 매일 전일 기준 등락률을 확인해 $$$설정.KRX.threshold$$$% 이상 변동 시 알려드립니다.}
문장::TELEGRAM.주식관심종목검색실패문장
{"$$$세션.krx_target_name$$$" 종목을 찾을 수 없습니다. 정확한 종목명으로 다시 시도해주세요.}
문장::TELEGRAM.주식관심종목삭제완료문장
{$$$세션.krx_target_name$$$을(를) 관심종목에서 삭제했습니다.}
문장::TELEGRAM.주식관심종목삭제실패문장
{$$$세션.krx_target_name$$$은(는) 등록된 관심종목이 아닙니다.}
문장::TELEGRAM.주식관심종목조회빈목록문장
{등록된 관심종목이 없습니다. "주식 관심종목 추가 삼성전자"처럼 말씀해주세요.}
문장::TELEGRAM.주식관심종목추가한도초과문장
{이미 관심종목이 5개 등록되어 있어 더 추가할 수 없습니다. 기존 종목을 삭제한 후 다시 시도해주세요.}
처리::FLOW.기상청관심지역초기화
{
  만약에(참)
    함수.저장(kma_watch_csv,없음)
    함수.저장(kma_seed_found,0)
    함수.저장(kma_ini_category,KMA_WATCHLIST)
    함수.저장(kma_ini_clear_value,없음)
    함수.저장(kma_ini_pipe,|)
    처리.기상청지역시드확인1
}
처리::FLOW.기상청지역시드확인1
{
  만약에(설정.KMA_WATCHLIST.지역1 != NULL) 그리고(설정.KMA_WATCHLIST.지역1 != 없음) 그리고(세션.kma_seed_found != 1)
    함수.저장(kma_watch_csv,설정.KMA_WATCHLIST.지역1)
    함수.저장(kma_seed_found,1)
    처리.기상청지역시드확인2
  그외그외(설정.KMA_WATCHLIST.지역1 != NULL) 그리고(설정.KMA_WATCHLIST.지역1 != 없음)
    함수.붙이기(kma_watch_csv,|,설정.KMA_WATCHLIST.지역1)
    처리.기상청지역시드확인2
  그외
    처리.기상청지역시드확인2
}
처리::FLOW.기상청지역시드확인2
{
  만약에(설정.KMA_WATCHLIST.지역2 != NULL) 그리고(설정.KMA_WATCHLIST.지역2 != 없음) 그리고(세션.kma_seed_found != 1)
    함수.저장(kma_watch_csv,설정.KMA_WATCHLIST.지역2)
    함수.저장(kma_seed_found,1)
    처리.기상청지역시드확인3
  그외그외(설정.KMA_WATCHLIST.지역2 != NULL) 그리고(설정.KMA_WATCHLIST.지역2 != 없음)
    함수.붙이기(kma_watch_csv,|,설정.KMA_WATCHLIST.지역2)
    처리.기상청지역시드확인3
  그외
    처리.기상청지역시드확인3
}
처리::FLOW.기상청지역시드확인3
{
  만약에(설정.KMA_WATCHLIST.지역3 != NULL) 그리고(설정.KMA_WATCHLIST.지역3 != 없음) 그리고(세션.kma_seed_found != 1)
    함수.저장(kma_watch_csv,설정.KMA_WATCHLIST.지역3)
    함수.저장(kma_seed_found,1)
    처리.기상청지역시드확인4
  그외그외(설정.KMA_WATCHLIST.지역3 != NULL) 그리고(설정.KMA_WATCHLIST.지역3 != 없음)
    함수.붙이기(kma_watch_csv,|,설정.KMA_WATCHLIST.지역3)
    처리.기상청지역시드확인4
  그외
    처리.기상청지역시드확인4
}
처리::FLOW.기상청지역시드확인4
{
  만약에(설정.KMA_WATCHLIST.지역4 != NULL) 그리고(설정.KMA_WATCHLIST.지역4 != 없음) 그리고(세션.kma_seed_found != 1)
    함수.저장(kma_watch_csv,설정.KMA_WATCHLIST.지역4)
    함수.저장(kma_seed_found,1)
    처리.기상청지역시드확인5
  그외그외(설정.KMA_WATCHLIST.지역4 != NULL) 그리고(설정.KMA_WATCHLIST.지역4 != 없음)
    함수.붙이기(kma_watch_csv,|,설정.KMA_WATCHLIST.지역4)
    처리.기상청지역시드확인5
  그외
    처리.기상청지역시드확인5
}
처리::FLOW.기상청지역시드확인5
{
  만약에(설정.KMA_WATCHLIST.지역5 != NULL) 그리고(설정.KMA_WATCHLIST.지역5 != 없음) 그리고(세션.kma_seed_found != 1)
    함수.저장(kma_watch_csv,설정.KMA_WATCHLIST.지역5)
    함수.저장(kma_seed_found,1)
    로그.출력(기상청 관심지역 시드 로딩 완료)
  그외그외(설정.KMA_WATCHLIST.지역5 != NULL) 그리고(설정.KMA_WATCHLIST.지역5 != 없음)
    함수.붙이기(kma_watch_csv,|,설정.KMA_WATCHLIST.지역5)
    로그.출력(기상청 관심지역 시드 로딩 완료)
  그외
    로그.출력(기상청 관심지역 시드 로딩 완료)
}
처리::FLOW.기상알림확인처리
{
  만약에(참)
    함수.날짜(kma_alert_hour_now,%H)
    타이머.기상알림타이머
    처리.기상알림시각비교
}
처리::FLOW.기상알림시각비교
{
  만약에(세션.kma_alert_hour_now == 설정.KMA.alert_hour)
    함수.저장(kma_mode,폴링)
    처리.KMA관심지역순회시작
  그외
    로그.출력(기상 알림 스킵 - 시각 불일치)
}
처리::TELEGRAM.텔레그램기상청명령처리
{
  만약에(참)
    함수.단어분리(kma_cmd_word_list,세션.chat_input_text)
    함수.단어합치기(cmd_rest,세션.리스트.kma_cmd_word_list,1)
    함수.앞자리비교(cmd_날씨,세션.cmd_rest,날씨)
    함수.앞자리비교(cmd_지역,세션.cmd_rest,지역)
    처리.텔레그램기상청명령분기
}
처리::TELEGRAM.텔레그램기상청명령분기
{
  만약에(세션.cmd_날씨 == 1)
    처리.텔레그램기상청날씨명령처리
  그외그외(세션.cmd_지역 == 1)
    처리.텔레그램기상청지역명령처리
  그외
    전송.명령모름응답
}
처리::TELEGRAM.텔레그램기상청날씨명령처리
{
  만약에(참)
    함수.단어분리(kma_weather_word_list,세션.cmd_rest)
    함수.단어합치기(kma_target_name,세션.리스트.kma_weather_word_list,1)
    함수.저장(kma_mode,조회단건)
    처리.KMA지역명확정
}
처리::KMA.KMA지역명확정
{
  만약에(세션.kma_target_name != NULL)
    처리.KMA지역좌표확인
  그외
    함수.저장(kma_target_name,설정.KMA.default_region)
    처리.KMA지역좌표확인
}
처리::TELEGRAM.텔레그램기상청지역명령처리
{
  만약에(참)
    함수.단어분리(kma_region_word_list,세션.cmd_rest)
    함수.단어합치기(kma_region_rest,세션.리스트.kma_region_word_list,1)
    함수.앞자리비교(cmd_지역추가,세션.kma_region_rest,추가)
    함수.앞자리비교(cmd_지역삭제,세션.kma_region_rest,삭제)
    함수.앞자리비교(cmd_지역조회,세션.kma_region_rest,조회)
    처리.텔레그램기상청지역명령분기
}
처리::TELEGRAM.텔레그램기상청지역명령분기
{
  만약에(세션.cmd_지역추가 == 1)
    처리.텔레그램기상청지역추가명령처리
  그외그외(세션.cmd_지역삭제 == 1)
    처리.텔레그램기상청지역삭제명령처리
  그외그외(세션.cmd_지역조회 == 1)
    처리.텔레그램기상청지역조회명령처리
  그외
    전송.명령모름응답
}
처리::TELEGRAM.텔레그램기상청지역조회명령처리
{
  만약에(참)
    함수.저장(kma_mode,지역조회)
    처리.KMA관심지역순회시작
}
처리::TELEGRAM.텔레그램기상청지역추가명령처리
{
  만약에(참)
    함수.단어분리(kma_add_word_list,세션.kma_region_rest)
    함수.단어합치기(kma_target_name,세션.리스트.kma_add_word_list,1)
    처리.KMA지역명검증
}
처리::KMA.KMA지역명검증
{
  만약에(세션.kma_target_name == 서울)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 부산)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 대구)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 인천)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 광주)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 대전)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 울산)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 세종)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 수원)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외그외(세션.kma_target_name == 제주)
    함수.저장(kma_region_found,1)
    처리.KMA지역추가확정
  그외
    함수.저장(kma_region_found,0)
    처리.KMA지역추가확정
}
처리::KMA.KMA지역추가확정
{
  만약에(세션.kma_region_found == 0)
    함수.저장(kma_reply_text,문장.KMA지역미지원문장)
    전송.기상청응답전송
  그외
    함수.저장(kma_ini_found,0)
    처리.KMA지역ini빈슬롯찾기1
}
처리::KMA.KMA지역ini빈슬롯찾기1
{
  만약에(설정.KMA_WATCHLIST.지역1 == NULL)
    함수.저장(kma_ini_slot_key,지역1)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외그외(설정.KMA_WATCHLIST.지역1 == 없음)
    함수.저장(kma_ini_slot_key,지역1)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외
    처리.KMA지역ini빈슬롯찾기2
}
처리::KMA.KMA지역ini빈슬롯찾기2
{
  만약에(설정.KMA_WATCHLIST.지역2 == NULL)
    함수.저장(kma_ini_slot_key,지역2)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외그외(설정.KMA_WATCHLIST.지역2 == 없음)
    함수.저장(kma_ini_slot_key,지역2)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외
    처리.KMA지역ini빈슬롯찾기3
}
처리::KMA.KMA지역ini빈슬롯찾기3
{
  만약에(설정.KMA_WATCHLIST.지역3 == NULL)
    함수.저장(kma_ini_slot_key,지역3)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외그외(설정.KMA_WATCHLIST.지역3 == 없음)
    함수.저장(kma_ini_slot_key,지역3)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외
    처리.KMA지역ini빈슬롯찾기4
}
처리::KMA.KMA지역ini빈슬롯찾기4
{
  만약에(설정.KMA_WATCHLIST.지역4 == NULL)
    함수.저장(kma_ini_slot_key,지역4)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외그외(설정.KMA_WATCHLIST.지역4 == 없음)
    함수.저장(kma_ini_slot_key,지역4)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외
    처리.KMA지역ini빈슬롯찾기5
}
처리::KMA.KMA지역ini빈슬롯찾기5
{
  만약에(설정.KMA_WATCHLIST.지역5 == NULL)
    함수.저장(kma_ini_slot_key,지역5)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외그외(설정.KMA_WATCHLIST.지역5 == 없음)
    함수.저장(kma_ini_slot_key,지역5)
    함수.저장(kma_ini_found,1)
    처리.KMA지역ini쓰기
  그외
    처리.KMA지역ini쓰기
}
처리::KMA.KMA지역ini쓰기
{
  만약에(세션.kma_ini_found == 1)
    함수.설정저장(세션.kma_ini_category,세션.kma_ini_slot_key,세션.kma_target_name)
    처리.KMA지역추가세션갱신
  그외
    함수.저장(kma_reply_text,문장.KMA지역추가한도초과문장)
    전송.기상청응답전송
}
처리::KMA.KMA지역추가세션갱신
{
  만약에(세션.kma_watch_csv == 없음)
    함수.저장(kma_watch_csv,세션.kma_target_name)
    함수.저장(kma_reply_text,문장.KMA지역추가완료문장)
    전송.기상청응답전송
  그외
    함수.붙이기(kma_watch_csv,세션.kma_ini_pipe,세션.kma_target_name)
    함수.저장(kma_reply_text,문장.KMA지역추가완료문장)
    전송.기상청응답전송
}
처리::TELEGRAM.텔레그램기상청지역삭제명령처리
{
  만약에(참)
    함수.단어분리(kma_del_word_list,세션.kma_region_rest)
    함수.단어합치기(kma_target_name,세션.리스트.kma_del_word_list,1)
    함수.쪼개기(kma_watch_list,세션.kma_watch_csv,|)
    함수.저장(kma_del_idx,0)
    함수.저장(kma_del_found,0)
    함수.저장(kma_new_csv,없음)
    함수.저장(kma_new_found,0)
    처리.KMA지역삭제순회
}
처리::KMA.KMA지역삭제순회
{
  만약에(세션.kma_del_idx >= 세션.리스트.kma_watch_list.SIZE)
    처리.KMA지역삭제완료
  그외
    처리.KMA지역삭제항목검사
}
처리::KMA.KMA지역삭제항목검사
{
  만약에(세션.리스트.kma_watch_list[세션.kma_del_idx] == 세션.kma_target_name)
    함수.더하기(kma_del_found,세션.kma_del_found,1)
    함수.더하기(kma_del_idx,세션.kma_del_idx,1)
    처리.KMA지역삭제순회
  그외
    처리.KMA지역삭제보존
}
처리::KMA.KMA지역삭제보존
{
  만약에(세션.kma_new_found == 0)
    함수.저장(kma_new_csv,세션.리스트.kma_watch_list[세션.kma_del_idx])
    함수.저장(kma_new_found,1)
    함수.더하기(kma_del_idx,세션.kma_del_idx,1)
    처리.KMA지역삭제순회
  그외
    함수.붙이기(kma_new_csv,세션.kma_ini_pipe,세션.리스트.kma_watch_list[세션.kma_del_idx])
    함수.더하기(kma_del_idx,세션.kma_del_idx,1)
    처리.KMA지역삭제순회
}
처리::KMA.KMA지역삭제완료
{
  만약에(세션.kma_del_found > 0)
    함수.저장(kma_watch_csv,세션.kma_new_csv)
    함수.저장(kma_ini_del_found,0)
    처리.KMA지역ini삭제찾기1
  그외
    함수.저장(kma_reply_text,문장.KMA지역삭제실패문장)
    전송.기상청응답전송
}
처리::KMA.KMA지역ini삭제찾기1
{
  만약에(설정.KMA_WATCHLIST.지역1 == 세션.kma_target_name) 그리고(세션.kma_ini_del_found != 1)
    함수.저장(kma_ini_slot_key,지역1)
    함수.설정저장(세션.kma_ini_category,세션.kma_ini_slot_key,세션.kma_ini_clear_value)
    함수.저장(kma_ini_del_found,1)
    처리.KMA지역ini삭제찾기2
  그외
    처리.KMA지역ini삭제찾기2
}
처리::KMA.KMA지역ini삭제찾기2
{
  만약에(설정.KMA_WATCHLIST.지역2 == 세션.kma_target_name) 그리고(세션.kma_ini_del_found != 1)
    함수.저장(kma_ini_slot_key,지역2)
    함수.설정저장(세션.kma_ini_category,세션.kma_ini_slot_key,세션.kma_ini_clear_value)
    함수.저장(kma_ini_del_found,1)
    처리.KMA지역ini삭제찾기3
  그외
    처리.KMA지역ini삭제찾기3
}
처리::KMA.KMA지역ini삭제찾기3
{
  만약에(설정.KMA_WATCHLIST.지역3 == 세션.kma_target_name) 그리고(세션.kma_ini_del_found != 1)
    함수.저장(kma_ini_slot_key,지역3)
    함수.설정저장(세션.kma_ini_category,세션.kma_ini_slot_key,세션.kma_ini_clear_value)
    함수.저장(kma_ini_del_found,1)
    처리.KMA지역ini삭제찾기4
  그외
    처리.KMA지역ini삭제찾기4
}
처리::KMA.KMA지역ini삭제찾기4
{
  만약에(설정.KMA_WATCHLIST.지역4 == 세션.kma_target_name) 그리고(세션.kma_ini_del_found != 1)
    함수.저장(kma_ini_slot_key,지역4)
    함수.설정저장(세션.kma_ini_category,세션.kma_ini_slot_key,세션.kma_ini_clear_value)
    함수.저장(kma_ini_del_found,1)
    처리.KMA지역ini삭제찾기5
  그외
    처리.KMA지역ini삭제찾기5
}
처리::KMA.KMA지역ini삭제찾기5
{
  만약에(설정.KMA_WATCHLIST.지역5 == 세션.kma_target_name) 그리고(세션.kma_ini_del_found != 1)
    함수.저장(kma_ini_slot_key,지역5)
    함수.설정저장(세션.kma_ini_category,세션.kma_ini_slot_key,세션.kma_ini_clear_value)
    처리.KMA지역삭제응답
  그외
    처리.KMA지역삭제응답
}
처리::KMA.KMA지역삭제응답
{
  만약에(참)
    함수.저장(kma_reply_text,문장.KMA지역삭제완료문장)
    전송.기상청응답전송
}
처리::KMA.KMA지역좌표확인
{
  만약에(세션.kma_target_name == 서울)
    함수.저장(kma_nx,60)
    함수.저장(kma_ny,127)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 부산)
    함수.저장(kma_nx,98)
    함수.저장(kma_ny,76)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 대구)
    함수.저장(kma_nx,89)
    함수.저장(kma_ny,90)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 인천)
    함수.저장(kma_nx,55)
    함수.저장(kma_ny,124)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 광주)
    함수.저장(kma_nx,58)
    함수.저장(kma_ny,74)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 대전)
    함수.저장(kma_nx,67)
    함수.저장(kma_ny,100)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 울산)
    함수.저장(kma_nx,102)
    함수.저장(kma_ny,84)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 세종)
    함수.저장(kma_nx,66)
    함수.저장(kma_ny,103)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 수원)
    함수.저장(kma_nx,60)
    함수.저장(kma_ny,121)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외그외(세션.kma_target_name == 제주)
    함수.저장(kma_nx,52)
    함수.저장(kma_ny,38)
    함수.저장(kma_region_found,1)
    처리.KMA지역확인완료
  그외
    함수.저장(kma_region_found,0)
    처리.KMA지역확인완료
}
처리::KMA.KMA지역확인완료
{
  만약에(세션.kma_region_found == 0) 그리고(세션.kma_mode == 조회단건)
    함수.저장(kma_reply_text,문장.KMA지역미지원문장)
    전송.기상청응답전송
  그외그외(세션.kma_region_found == 0)
    함수.더하기(kma_walk_idx,세션.kma_walk_idx,1)
    처리.KMA관심지역순회다음
  그외
    처리.KMA시각계산
}
처리::KMA.KMA시각계산
{
  만약에(참)
    함수.날짜(kma_base_date,%Y%m%d)
    함수.날짜(kma_hour,%H)
    처리.KMA발표시각분기
}
처리::KMA.KMA발표시각분기
{
  만약에(세션.kma_hour >= 23)
    함수.저장(kma_base_time,2300)
    함수.저장(kma_base_hour,23)
    전송.KMA단기예보조회전송
  그외그외(세션.kma_hour >= 20)
    함수.저장(kma_base_time,2000)
    함수.저장(kma_base_hour,20)
    전송.KMA단기예보조회전송
  그외그외(세션.kma_hour >= 17)
    함수.저장(kma_base_time,1700)
    함수.저장(kma_base_hour,17)
    전송.KMA단기예보조회전송
  그외그외(세션.kma_hour >= 14)
    함수.저장(kma_base_time,1400)
    함수.저장(kma_base_hour,14)
    전송.KMA단기예보조회전송
  그외그외(세션.kma_hour >= 11)
    함수.저장(kma_base_time,1100)
    함수.저장(kma_base_hour,11)
    전송.KMA단기예보조회전송
  그외그외(세션.kma_hour >= 8)
    함수.저장(kma_base_time,0800)
    함수.저장(kma_base_hour,8)
    전송.KMA단기예보조회전송
  그외그외(세션.kma_hour >= 5)
    함수.저장(kma_base_time,0500)
    함수.저장(kma_base_hour,5)
    전송.KMA단기예보조회전송
  그외
    함수.저장(kma_base_time,0200)
    함수.저장(kma_base_hour,2)
    전송.KMA단기예보조회전송
}
처리::KMA.KMA응답분기처리
{
  만약에(세션.kma_mode == 조회단건)
    함수.객체저장(kma_items,수신메시지.response.body.items.item)
    함수.저장(kma_item_idx,0)
    함수.저장(kma_tmp,없음)
    함수.저장(kma_pop,없음)
    함수.저장(kma_sky_code,없음)
    함수.저장(kma_pty_code,없음)
    처리.KMA항목순회
  그외그외(세션.kma_mode == 폴링)
    함수.객체저장(kma_items,수신메시지.response.body.items.item)
    함수.저장(kma_item_idx,0)
    함수.저장(kma_tmp,없음)
    함수.저장(kma_pop,없음)
    함수.저장(kma_sky_code,없음)
    함수.저장(kma_pty_code,없음)
    처리.KMA항목순회
  그외그외(세션.kma_mode == 지역조회)
    함수.객체저장(kma_items,수신메시지.response.body.items.item)
    함수.저장(kma_item_idx,0)
    함수.저장(kma_tmp,없음)
    함수.저장(kma_pop,없음)
    함수.저장(kma_sky_code,없음)
    함수.저장(kma_pty_code,없음)
    처리.KMA항목순회
  그외
    로그.출력(KMA 알 수 없는 응답 모드)
}
처리::KMA.KMA항목순회
{
  만약에(세션.객체.kma_items[세션.kma_item_idx].category == NULL)
    처리.KMA요약생성
  그외
    처리.KMA항목검사
}
처리::KMA.KMA항목검사
{
  만약에(세션.객체.kma_items[세션.kma_item_idx].category == TMP)
    함수.저장(kma_tmp,세션.객체.kma_items[세션.kma_item_idx].fcstValue)
    함수.더하기(kma_item_idx,세션.kma_item_idx,1)
    처리.KMA항목순회
  그외그외(세션.객체.kma_items[세션.kma_item_idx].category == POP)
    함수.저장(kma_pop,세션.객체.kma_items[세션.kma_item_idx].fcstValue)
    함수.더하기(kma_item_idx,세션.kma_item_idx,1)
    처리.KMA항목순회
  그외그외(세션.객체.kma_items[세션.kma_item_idx].category == SKY)
    함수.저장(kma_sky_code,세션.객체.kma_items[세션.kma_item_idx].fcstValue)
    함수.더하기(kma_item_idx,세션.kma_item_idx,1)
    처리.KMA항목순회
  그외그외(세션.객체.kma_items[세션.kma_item_idx].category == PTY)
    함수.저장(kma_pty_code,세션.객체.kma_items[세션.kma_item_idx].fcstValue)
    함수.더하기(kma_item_idx,세션.kma_item_idx,1)
    처리.KMA항목순회
  그외
    함수.더하기(kma_item_idx,세션.kma_item_idx,1)
    처리.KMA항목순회
}
처리::KMA.KMA요약생성
{
  만약에(세션.kma_pty_code == 1)
    함수.저장(kma_weather_text,비)
    처리.KMA임계값판정
  그외그외(세션.kma_pty_code == 2)
    함수.저장(kma_weather_text,비또는눈)
    처리.KMA임계값판정
  그외그외(세션.kma_pty_code == 3)
    함수.저장(kma_weather_text,눈)
    처리.KMA임계값판정
  그외그외(세션.kma_pty_code == 4)
    함수.저장(kma_weather_text,소나기)
    처리.KMA임계값판정
  그외그외(세션.kma_sky_code == 1)
    함수.저장(kma_weather_text,맑음)
    처리.KMA임계값판정
  그외그외(세션.kma_sky_code == 3)
    함수.저장(kma_weather_text,구름많음)
    처리.KMA임계값판정
  그외그외(세션.kma_sky_code == 4)
    함수.저장(kma_weather_text,흐림)
    처리.KMA임계값판정
  그외
    함수.저장(kma_weather_text,정보없음)
    처리.KMA임계값판정
}
처리::KMA.KMA임계값판정
{
  만약에(세션.kma_mode == 조회단건)
    처리.KMA단건응답조립
  그외그외(세션.kma_mode == 지역조회)
    처리.KMA지역조회라인추가
  그외그외(세션.kma_pty_code != 0)
    처리.KMA감시라인추가
  그외그외(세션.kma_pop >= 설정.KMA.threshold)
    처리.KMA감시라인추가
  그외
    함수.더하기(kma_walk_idx,세션.kma_walk_idx,1)
    처리.KMA관심지역순회다음
}
처리::KMA.KMA단건응답조립
{
  만약에(참)
    함수.저장(kma_reply_text,문장.KMA날씨요약문장)
    전송.기상청응답전송
}
처리::KMA.KMA감시라인추가
{
  만약에(세션.kma_walk_found == 0)
    함수.저장(kma_walk_lines,문장.KMA감시알림라인문장)
    함수.저장(kma_walk_found,1)
    함수.더하기(kma_walk_idx,세션.kma_walk_idx,1)
    처리.KMA관심지역순회다음
  그외
    함수.붙이기(kma_walk_lines,|,문장.KMA감시알림라인문장)
    함수.더하기(kma_walk_idx,세션.kma_walk_idx,1)
    처리.KMA관심지역순회다음
}
처리::KMA.KMA지역조회라인추가
{
  만약에(세션.kma_walk_found == 0)
    함수.저장(kma_walk_lines,문장.KMA날씨요약문장)
    함수.저장(kma_walk_found,1)
    함수.더하기(kma_walk_idx,세션.kma_walk_idx,1)
    처리.KMA관심지역순회다음
  그외
    함수.붙이기(kma_walk_lines,|,문장.KMA날씨요약문장)
    함수.더하기(kma_walk_idx,세션.kma_walk_idx,1)
    처리.KMA관심지역순회다음
}
처리::KMA.KMA관심지역순회시작
{
  만약에(세션.kma_watch_csv == 없음)
    함수.저장(kma_walk_lines,없음)
    처리.KMA관심지역순회완료
  그외
    함수.쪼개기(kma_watch_list,세션.kma_watch_csv,|)
    함수.저장(kma_walk_idx,0)
    함수.저장(kma_walk_found,0)
    함수.저장(kma_walk_lines,없음)
    처리.KMA관심지역순회다음
}
처리::KMA.KMA관심지역순회다음
{
  만약에(세션.kma_walk_idx >= 세션.리스트.kma_watch_list.SIZE)
    처리.KMA관심지역순회완료
  그외
    함수.저장(kma_target_name,세션.리스트.kma_watch_list[세션.kma_walk_idx])
    처리.KMA지역좌표확인
}
처리::KMA.KMA관심지역순회완료
{
  만약에(세션.kma_mode == 지역조회) 그리고(세션.kma_walk_lines != 없음)
    함수.저장(kma_reply_text,세션.kma_walk_lines)
    전송.기상청응답전송
  그외그외(세션.kma_mode == 지역조회)
    함수.저장(kma_reply_text,문장.KMA지역조회빈목록문장)
    전송.기상청응답전송
  그외그외(세션.kma_walk_lines != 없음)
    함수.저장(kma_watch_alert_text,세션.kma_walk_lines)
    전송.기상알림텔레그램전송
  그외
    로그.출력(기상청 관심지역 감시 - 임계값 초과 지역 없음, 알림 생략)
}
전송::KMA.KMA단기예보조회전송
{
  전송메시지.메소드 = GET
  전송메시지.주소.도메인 = 설정.KMA.domain
  전송메시지.주소.경로 = /getVilageFcst
  전송메시지.주소.파라미터[0].key = serviceKey
  전송메시지.주소.파라미터[0].val = 설정.K_DATA.service_key
  전송메시지.주소.파라미터[1].key = pageNo
  전송메시지.주소.파라미터[1].val = 1
  전송메시지.주소.파라미터[2].key = numOfRows
  전송메시지.주소.파라미터[2].val = 12
  전송메시지.주소.파라미터[3].key = dataType
  전송메시지.주소.파라미터[3].val = JSON
  전송메시지.주소.파라미터[4].key = base_date
  전송메시지.주소.파라미터[4].val = 세션.kma_base_date
  전송메시지.주소.파라미터[5].key = base_time
  전송메시지.주소.파라미터[5].val = 세션.kma_base_time
  전송메시지.주소.파라미터[6].key = nx
  전송메시지.주소.파라미터[6].val = 세션.kma_nx
  전송메시지.주소.파라미터[7].key = ny
  전송메시지.주소.파라미터[7].val = 세션.kma_ny
}
전송::FLOW.기상청응답전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 세션.kma_reply_text
}
전송::FLOW.기상알림텔레그램전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 문장.기상알림문장
}
타이머::KMA.기상알림타이머
{
  전송메시지.이벤트명 = 기상알림틱
  전송메시지.시간 = 3600000
}
문장::KMA.KMA날씨요약문장
{$$$세션.kma_target_name$$$ 날씨: $$$세션.kma_weather_text$$$, 기온 $$$세션.kma_tmp$$$도, 강수확률 $$$세션.kma_pop$$$% (오늘 $$$세션.kma_base_hour$$$시 발표 기준)}
문장::KMA.KMA감시알림라인문장
{$$$세션.kma_target_name$$$ $$$세션.kma_weather_text$$$ 강수확률 $$$세션.kma_pop$$$%}
문장::TELEGRAM.기상알림문장
{[기상청 날씨 알림] 강수확률 $$$설정.KMA.threshold$$$% 이상 또는 비/눈 예보가 있는 관심지역이 있습니다.
$$$세션.kma_watch_alert_text$$$}
문장::TELEGRAM.KMA지역미지원문장
{"$$$세션.kma_target_name$$$"은(는) 아직 지원하지 않는 지역입니다. 지원 지역: 서울, 부산, 대구, 인천, 광주, 대전, 울산, 세종, 수원, 제주}
문장::TELEGRAM.KMA지역추가완료문장
{$$$세션.kma_target_name$$$을(를) 관심지역에 추가했습니다. 매일 $$$설정.KMA.alert_hour$$$시경 강수확률 $$$설정.KMA.threshold$$$% 이상 또는 비/눈 예보 시 알려드립니다.}
문장::TELEGRAM.KMA지역삭제완료문장
{$$$세션.kma_target_name$$$을(를) 관심지역에서 삭제했습니다.}
문장::TELEGRAM.KMA지역삭제실패문장
{$$$세션.kma_target_name$$$은(는) 등록된 관심지역이 아닙니다.}
문장::TELEGRAM.KMA지역조회빈목록문장
{등록된 관심지역이 없습니다. "기상청 지역 추가 서울"처럼 말씀해주세요.}
문장::TELEGRAM.KMA지역추가한도초과문장
{이미 관심지역이 5개 등록되어 있어 더 추가할 수 없습니다. 기존 지역을 삭제한 후 다시 시도해주세요.}
처리::FLOW.미세먼지관심지역초기화
{
  만약에(참)
    함수.저장(keco_watch_csv,없음)
    함수.저장(keco_seed_found,0)
    함수.저장(keco_ini_category,KECO_WATCHLIST)
    함수.저장(keco_ini_clear_value,없음)
    함수.저장(keco_ini_pipe,|)
    처리.미세먼지지역시드확인1
}
처리::FLOW.미세먼지지역시드확인1
{
  만약에(설정.KECO_WATCHLIST.지역1 != NULL) 그리고(설정.KECO_WATCHLIST.지역1 != 없음) 그리고(세션.keco_seed_found != 1)
    함수.저장(keco_watch_csv,설정.KECO_WATCHLIST.지역1)
    함수.저장(keco_seed_found,1)
    처리.미세먼지지역시드확인2
  그외그외(설정.KECO_WATCHLIST.지역1 != NULL) 그리고(설정.KECO_WATCHLIST.지역1 != 없음)
    함수.붙이기(keco_watch_csv,|,설정.KECO_WATCHLIST.지역1)
    처리.미세먼지지역시드확인2
  그외
    처리.미세먼지지역시드확인2
}
처리::FLOW.미세먼지지역시드확인2
{
  만약에(설정.KECO_WATCHLIST.지역2 != NULL) 그리고(설정.KECO_WATCHLIST.지역2 != 없음) 그리고(세션.keco_seed_found != 1)
    함수.저장(keco_watch_csv,설정.KECO_WATCHLIST.지역2)
    함수.저장(keco_seed_found,1)
    처리.미세먼지지역시드확인3
  그외그외(설정.KECO_WATCHLIST.지역2 != NULL) 그리고(설정.KECO_WATCHLIST.지역2 != 없음)
    함수.붙이기(keco_watch_csv,|,설정.KECO_WATCHLIST.지역2)
    처리.미세먼지지역시드확인3
  그외
    처리.미세먼지지역시드확인3
}
처리::FLOW.미세먼지지역시드확인3
{
  만약에(설정.KECO_WATCHLIST.지역3 != NULL) 그리고(설정.KECO_WATCHLIST.지역3 != 없음) 그리고(세션.keco_seed_found != 1)
    함수.저장(keco_watch_csv,설정.KECO_WATCHLIST.지역3)
    함수.저장(keco_seed_found,1)
    처리.미세먼지지역시드확인4
  그외그외(설정.KECO_WATCHLIST.지역3 != NULL) 그리고(설정.KECO_WATCHLIST.지역3 != 없음)
    함수.붙이기(keco_watch_csv,|,설정.KECO_WATCHLIST.지역3)
    처리.미세먼지지역시드확인4
  그외
    처리.미세먼지지역시드확인4
}
처리::FLOW.미세먼지지역시드확인4
{
  만약에(설정.KECO_WATCHLIST.지역4 != NULL) 그리고(설정.KECO_WATCHLIST.지역4 != 없음) 그리고(세션.keco_seed_found != 1)
    함수.저장(keco_watch_csv,설정.KECO_WATCHLIST.지역4)
    함수.저장(keco_seed_found,1)
    처리.미세먼지지역시드확인5
  그외그외(설정.KECO_WATCHLIST.지역4 != NULL) 그리고(설정.KECO_WATCHLIST.지역4 != 없음)
    함수.붙이기(keco_watch_csv,|,설정.KECO_WATCHLIST.지역4)
    처리.미세먼지지역시드확인5
  그외
    처리.미세먼지지역시드확인5
}
처리::FLOW.미세먼지지역시드확인5
{
  만약에(설정.KECO_WATCHLIST.지역5 != NULL) 그리고(설정.KECO_WATCHLIST.지역5 != 없음) 그리고(세션.keco_seed_found != 1)
    함수.저장(keco_watch_csv,설정.KECO_WATCHLIST.지역5)
    함수.저장(keco_seed_found,1)
    로그.출력(미세먼지 관심지역 시드 로딩 완료)
  그외그외(설정.KECO_WATCHLIST.지역5 != NULL) 그리고(설정.KECO_WATCHLIST.지역5 != 없음)
    함수.붙이기(keco_watch_csv,|,설정.KECO_WATCHLIST.지역5)
    로그.출력(미세먼지 관심지역 시드 로딩 완료)
  그외
    로그.출력(미세먼지 관심지역 시드 로딩 완료)
}
처리::FLOW.미세먼지알림확인처리
{
  만약에(참)
    함수.날짜(keco_alert_hour_now,%H)
    타이머.미세먼지알림타이머
    처리.미세먼지알림시각비교
}
처리::FLOW.미세먼지알림시각비교
{
  만약에(세션.keco_alert_hour_now == 설정.KECO.alert_hour)
    함수.저장(keco_mode,폴링)
    처리.KECO관심지역순회시작
  그외
    로그.출력(미세먼지 알림 스킵 - 시각 불일치)
}
처리::TELEGRAM.텔레그램미세먼지명령처리
{
  만약에(참)
    함수.단어분리(keco_cmd_word_list,세션.chat_input_text)
    함수.단어합치기(cmd_rest,세션.리스트.keco_cmd_word_list,1)
    함수.앞자리비교(cmd_지역,세션.cmd_rest,지역)
    처리.텔레그램미세먼지명령분기
}
처리::TELEGRAM.텔레그램미세먼지명령분기
{
  만약에(세션.cmd_지역 == 1)
    처리.텔레그램미세먼지지역명령처리
  그외
    함수.저장(keco_target_name,세션.cmd_rest)
    함수.저장(keco_mode,조회단건)
    처리.KECO지역명확정
}
처리::KECO.KECO지역명확정
{
  만약에(세션.keco_target_name != NULL)
    처리.KECO지역확인
  그외
    함수.저장(keco_target_name,설정.KECO.default_region)
    처리.KECO지역확인
}
처리::TELEGRAM.텔레그램미세먼지지역명령처리
{
  만약에(참)
    함수.단어분리(keco_region_word_list,세션.cmd_rest)
    함수.단어합치기(keco_region_rest,세션.리스트.keco_region_word_list,1)
    함수.앞자리비교(cmd_지역추가,세션.keco_region_rest,추가)
    함수.앞자리비교(cmd_지역삭제,세션.keco_region_rest,삭제)
    함수.앞자리비교(cmd_지역조회,세션.keco_region_rest,조회)
    처리.텔레그램미세먼지지역명령분기
}
처리::TELEGRAM.텔레그램미세먼지지역명령분기
{
  만약에(세션.cmd_지역추가 == 1)
    처리.텔레그램미세먼지지역추가명령처리
  그외그외(세션.cmd_지역삭제 == 1)
    처리.텔레그램미세먼지지역삭제명령처리
  그외그외(세션.cmd_지역조회 == 1)
    처리.텔레그램미세먼지지역조회명령처리
  그외
    전송.명령모름응답
}
처리::TELEGRAM.텔레그램미세먼지지역조회명령처리
{
  만약에(참)
    함수.저장(keco_mode,지역조회)
    처리.KECO관심지역순회시작
}
처리::TELEGRAM.텔레그램미세먼지지역추가명령처리
{
  만약에(참)
    함수.단어분리(keco_add_word_list,세션.keco_region_rest)
    함수.단어합치기(keco_target_name,세션.리스트.keco_add_word_list,1)
    처리.KECO지역명검증
}
처리::KECO.KECO지역명검증
{
  만약에(세션.keco_target_name == 서울)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 부산)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 대구)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 인천)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 광주)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 대전)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 울산)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 세종)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 경기)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 강원)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 충북)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 충남)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 전북)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 전남)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 경북)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 경남)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외그외(세션.keco_target_name == 제주)
    함수.저장(keco_region_found,1)
    처리.KECO지역추가확정
  그외
    함수.저장(keco_region_found,0)
    처리.KECO지역추가확정
}
처리::KECO.KECO지역추가확정
{
  만약에(세션.keco_region_found == 0)
    함수.저장(keco_reply_text,문장.KECO지역미지원문장)
    전송.미세먼지응답전송
  그외
    함수.저장(keco_ini_found,0)
    처리.KECO지역ini빈슬롯찾기1
}
처리::KECO.KECO지역ini빈슬롯찾기1
{
  만약에(설정.KECO_WATCHLIST.지역1 == NULL)
    함수.저장(keco_ini_slot_key,지역1)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외그외(설정.KECO_WATCHLIST.지역1 == 없음)
    함수.저장(keco_ini_slot_key,지역1)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외
    처리.KECO지역ini빈슬롯찾기2
}
처리::KECO.KECO지역ini빈슬롯찾기2
{
  만약에(설정.KECO_WATCHLIST.지역2 == NULL)
    함수.저장(keco_ini_slot_key,지역2)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외그외(설정.KECO_WATCHLIST.지역2 == 없음)
    함수.저장(keco_ini_slot_key,지역2)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외
    처리.KECO지역ini빈슬롯찾기3
}
처리::KECO.KECO지역ini빈슬롯찾기3
{
  만약에(설정.KECO_WATCHLIST.지역3 == NULL)
    함수.저장(keco_ini_slot_key,지역3)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외그외(설정.KECO_WATCHLIST.지역3 == 없음)
    함수.저장(keco_ini_slot_key,지역3)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외
    처리.KECO지역ini빈슬롯찾기4
}
처리::KECO.KECO지역ini빈슬롯찾기4
{
  만약에(설정.KECO_WATCHLIST.지역4 == NULL)
    함수.저장(keco_ini_slot_key,지역4)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외그외(설정.KECO_WATCHLIST.지역4 == 없음)
    함수.저장(keco_ini_slot_key,지역4)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외
    처리.KECO지역ini빈슬롯찾기5
}
처리::KECO.KECO지역ini빈슬롯찾기5
{
  만약에(설정.KECO_WATCHLIST.지역5 == NULL)
    함수.저장(keco_ini_slot_key,지역5)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외그외(설정.KECO_WATCHLIST.지역5 == 없음)
    함수.저장(keco_ini_slot_key,지역5)
    함수.저장(keco_ini_found,1)
    처리.KECO지역ini쓰기
  그외
    처리.KECO지역ini쓰기
}
처리::KECO.KECO지역ini쓰기
{
  만약에(세션.keco_ini_found == 1)
    함수.설정저장(세션.keco_ini_category,세션.keco_ini_slot_key,세션.keco_target_name)
    처리.KECO지역추가세션갱신
  그외
    함수.저장(keco_reply_text,문장.KECO지역추가한도초과문장)
    전송.미세먼지응답전송
}
처리::KECO.KECO지역추가세션갱신
{
  만약에(세션.keco_watch_csv == 없음)
    함수.저장(keco_watch_csv,세션.keco_target_name)
    함수.저장(keco_reply_text,문장.KECO지역추가완료문장)
    전송.미세먼지응답전송
  그외
    함수.붙이기(keco_watch_csv,세션.keco_ini_pipe,세션.keco_target_name)
    함수.저장(keco_reply_text,문장.KECO지역추가완료문장)
    전송.미세먼지응답전송
}
처리::TELEGRAM.텔레그램미세먼지지역삭제명령처리
{
  만약에(참)
    함수.단어분리(keco_del_word_list,세션.keco_region_rest)
    함수.단어합치기(keco_target_name,세션.리스트.keco_del_word_list,1)
    함수.쪼개기(keco_watch_list,세션.keco_watch_csv,|)
    함수.저장(keco_del_idx,0)
    함수.저장(keco_del_found,0)
    함수.저장(keco_new_csv,없음)
    함수.저장(keco_new_found,0)
    처리.KECO지역삭제순회
}
처리::KECO.KECO지역삭제순회
{
  만약에(세션.keco_del_idx >= 세션.리스트.keco_watch_list.SIZE)
    처리.KECO지역삭제완료
  그외
    처리.KECO지역삭제항목검사
}
처리::KECO.KECO지역삭제항목검사
{
  만약에(세션.리스트.keco_watch_list[세션.keco_del_idx] == 세션.keco_target_name)
    함수.더하기(keco_del_found,세션.keco_del_found,1)
    함수.더하기(keco_del_idx,세션.keco_del_idx,1)
    처리.KECO지역삭제순회
  그외
    처리.KECO지역삭제보존
}
처리::KECO.KECO지역삭제보존
{
  만약에(세션.keco_new_found == 0)
    함수.저장(keco_new_csv,세션.리스트.keco_watch_list[세션.keco_del_idx])
    함수.저장(keco_new_found,1)
    함수.더하기(keco_del_idx,세션.keco_del_idx,1)
    처리.KECO지역삭제순회
  그외
    함수.붙이기(keco_new_csv,세션.keco_ini_pipe,세션.리스트.keco_watch_list[세션.keco_del_idx])
    함수.더하기(keco_del_idx,세션.keco_del_idx,1)
    처리.KECO지역삭제순회
}
처리::KECO.KECO지역삭제완료
{
  만약에(세션.keco_del_found > 0)
    함수.저장(keco_watch_csv,세션.keco_new_csv)
    함수.저장(keco_ini_del_found,0)
    처리.KECO지역ini삭제찾기1
  그외
    함수.저장(keco_reply_text,문장.KECO지역삭제실패문장)
    전송.미세먼지응답전송
}
처리::KECO.KECO지역ini삭제찾기1
{
  만약에(설정.KECO_WATCHLIST.지역1 == 세션.keco_target_name) 그리고(세션.keco_ini_del_found != 1)
    함수.저장(keco_ini_slot_key,지역1)
    함수.설정저장(세션.keco_ini_category,세션.keco_ini_slot_key,세션.keco_ini_clear_value)
    함수.저장(keco_ini_del_found,1)
    처리.KECO지역ini삭제찾기2
  그외
    처리.KECO지역ini삭제찾기2
}
처리::KECO.KECO지역ini삭제찾기2
{
  만약에(설정.KECO_WATCHLIST.지역2 == 세션.keco_target_name) 그리고(세션.keco_ini_del_found != 1)
    함수.저장(keco_ini_slot_key,지역2)
    함수.설정저장(세션.keco_ini_category,세션.keco_ini_slot_key,세션.keco_ini_clear_value)
    함수.저장(keco_ini_del_found,1)
    처리.KECO지역ini삭제찾기3
  그외
    처리.KECO지역ini삭제찾기3
}
처리::KECO.KECO지역ini삭제찾기3
{
  만약에(설정.KECO_WATCHLIST.지역3 == 세션.keco_target_name) 그리고(세션.keco_ini_del_found != 1)
    함수.저장(keco_ini_slot_key,지역3)
    함수.설정저장(세션.keco_ini_category,세션.keco_ini_slot_key,세션.keco_ini_clear_value)
    함수.저장(keco_ini_del_found,1)
    처리.KECO지역ini삭제찾기4
  그외
    처리.KECO지역ini삭제찾기4
}
처리::KECO.KECO지역ini삭제찾기4
{
  만약에(설정.KECO_WATCHLIST.지역4 == 세션.keco_target_name) 그리고(세션.keco_ini_del_found != 1)
    함수.저장(keco_ini_slot_key,지역4)
    함수.설정저장(세션.keco_ini_category,세션.keco_ini_slot_key,세션.keco_ini_clear_value)
    함수.저장(keco_ini_del_found,1)
    처리.KECO지역ini삭제찾기5
  그외
    처리.KECO지역ini삭제찾기5
}
처리::KECO.KECO지역ini삭제찾기5
{
  만약에(설정.KECO_WATCHLIST.지역5 == 세션.keco_target_name) 그리고(세션.keco_ini_del_found != 1)
    함수.저장(keco_ini_slot_key,지역5)
    함수.설정저장(세션.keco_ini_category,세션.keco_ini_slot_key,세션.keco_ini_clear_value)
    처리.KECO지역삭제응답
  그외
    처리.KECO지역삭제응답
}
처리::KECO.KECO지역삭제응답
{
  만약에(참)
    함수.저장(keco_reply_text,문장.KECO지역삭제완료문장)
    전송.미세먼지응답전송
}
처리::KECO.KECO지역확인
{
  만약에(세션.keco_target_name == 서울)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 부산)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 대구)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 인천)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 광주)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 대전)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 울산)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 세종)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 경기)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 강원)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 충북)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 충남)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 전북)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 전남)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 경북)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 경남)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외그외(세션.keco_target_name == 제주)
    함수.저장(keco_region_found,1)
    처리.KECO지역확인완료
  그외
    함수.저장(keco_region_found,0)
    처리.KECO지역확인완료
}
처리::KECO.KECO지역확인완료
{
  만약에(세션.keco_region_found == 0) 그리고(세션.keco_mode == 조회단건)
    함수.저장(keco_reply_text,문장.KECO지역미지원문장)
    전송.미세먼지응답전송
  그외그외(세션.keco_region_found == 0)
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
  그외
    전송.KECO대기오염조회전송
}
처리::KECO.KECO응답분기처리
{
  만약에(세션.keco_mode == 조회단건)
    함수.객체저장(keco_stations,수신메시지.response.body.items)
    처리.KECO첫측정소추출
  그외그외(세션.keco_mode == 폴링)
    함수.객체저장(keco_stations,수신메시지.response.body.items)
    처리.KECO첫측정소추출
  그외그외(세션.keco_mode == 지역조회)
    함수.객체저장(keco_stations,수신메시지.response.body.items)
    처리.KECO첫측정소추출
  그외
    로그.출력(KECO 알 수 없는 응답 모드)
}
처리::KECO.KECO첫측정소추출
{
  만약에(세션.객체.keco_stations[0].stationName != NULL)
    함수.저장(keco_datatime,세션.객체.keco_stations[0].dataTime)
    함수.저장(keco_khai_value,세션.객체.keco_stations[0].khaiValue)
    함수.저장(keco_khai_grade,세션.객체.keco_stations[0].khaiGrade)
    함수.저장(keco_pm10_value,세션.객체.keco_stations[0].pm10Value)
    함수.저장(keco_pm25_value,세션.객체.keco_stations[0].pm25Value)
    처리.KECO등급변환
  그외그외(세션.keco_mode == 조회단건)
    함수.저장(keco_reply_text,문장.KECO측정소없음문장)
    전송.미세먼지응답전송
  그외
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
}
처리::KECO.KECO등급변환
{
  만약에(세션.keco_khai_grade == 1)
    함수.저장(keco_grade_text,좋음)
    처리.KECO임계값판정
  그외그외(세션.keco_khai_grade == 2)
    함수.저장(keco_grade_text,보통)
    처리.KECO임계값판정
  그외그외(세션.keco_khai_grade == 3)
    함수.저장(keco_grade_text,나쁨)
    처리.KECO임계값판정
  그외그외(세션.keco_khai_grade == 4)
    함수.저장(keco_grade_text,매우나쁨)
    처리.KECO임계값판정
  그외
    함수.저장(keco_grade_text,정보없음)
    처리.KECO임계값판정
}
처리::KECO.KECO임계값판정
{
  만약에(세션.keco_mode == 조회단건)
    처리.KECO단건응답조립
  그외그외(세션.keco_mode == 지역조회)
    처리.KECO지역조회라인추가
  그외그외(세션.keco_khai_grade >= 3)
    처리.KECO감시라인추가
  그외
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
}
처리::KECO.KECO단건응답조립
{
  만약에(참)
    함수.저장(keco_reply_text,문장.KECO미세먼지요약문장)
    전송.미세먼지응답전송
}
처리::KECO.KECO감시라인추가
{
  만약에(세션.keco_walk_found == 0)
    함수.저장(keco_walk_lines,문장.KECO감시알림라인문장)
    함수.저장(keco_walk_found,1)
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
  그외
    함수.붙이기(keco_walk_lines,|,문장.KECO감시알림라인문장)
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
}
처리::KECO.KECO지역조회라인추가
{
  만약에(세션.keco_walk_found == 0)
    함수.저장(keco_walk_lines,문장.KECO미세먼지요약문장)
    함수.저장(keco_walk_found,1)
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
  그외
    함수.붙이기(keco_walk_lines,|,문장.KECO미세먼지요약문장)
    함수.더하기(keco_walk_idx,세션.keco_walk_idx,1)
    처리.KECO관심지역순회다음
}
처리::KECO.KECO관심지역순회시작
{
  만약에(세션.keco_watch_csv == 없음)
    함수.저장(keco_walk_lines,없음)
    처리.KECO관심지역순회완료
  그외
    함수.쪼개기(keco_watch_list,세션.keco_watch_csv,|)
    함수.저장(keco_walk_idx,0)
    함수.저장(keco_walk_found,0)
    함수.저장(keco_walk_lines,없음)
    처리.KECO관심지역순회다음
}
처리::KECO.KECO관심지역순회다음
{
  만약에(세션.keco_walk_idx >= 세션.리스트.keco_watch_list.SIZE)
    처리.KECO관심지역순회완료
  그외
    함수.저장(keco_target_name,세션.리스트.keco_watch_list[세션.keco_walk_idx])
    처리.KECO지역확인
}
처리::KECO.KECO관심지역순회완료
{
  만약에(세션.keco_mode == 지역조회) 그리고(세션.keco_walk_lines != 없음)
    함수.저장(keco_reply_text,세션.keco_walk_lines)
    전송.미세먼지응답전송
  그외그외(세션.keco_mode == 지역조회)
    함수.저장(keco_reply_text,문장.KECO지역조회빈목록문장)
    전송.미세먼지응답전송
  그외그외(세션.keco_walk_lines != 없음)
    함수.저장(keco_watch_alert_text,세션.keco_walk_lines)
    전송.미세먼지알림텔레그램전송
  그외
    로그.출력(미세먼지 관심지역 감시 - 나쁨 이상 지역 없음, 알림 생략)
}
전송::KECO.KECO대기오염조회전송
{
  전송메시지.메소드 = GET
  전송메시지.주소.도메인 = 설정.KECO.domain
  전송메시지.주소.경로 = /getCtprvnRltmMesureDnsty
  전송메시지.주소.파라미터[0].key = serviceKey
  전송메시지.주소.파라미터[0].val = 설정.K_DATA.service_key
  전송메시지.주소.파라미터[1].key = returnType
  전송메시지.주소.파라미터[1].val = json
  전송메시지.주소.파라미터[2].key = numOfRows
  전송메시지.주소.파라미터[2].val = 100
  전송메시지.주소.파라미터[3].key = pageNo
  전송메시지.주소.파라미터[3].val = 1
  전송메시지.주소.파라미터[4].key = sidoName
  전송메시지.주소.파라미터[4].val = 세션.keco_target_name
  전송메시지.주소.파라미터[5].key = ver
  전송메시지.주소.파라미터[5].val = 1.5
}
전송::FLOW.미세먼지응답전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 세션.keco_reply_text
}
전송::FLOW.미세먼지알림텔레그램전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 문장.미세먼지알림문장
}
타이머::KECO.미세먼지알림타이머
{
  전송메시지.이벤트명 = 미세먼지알림틱
  전송메시지.시간 = 3600000
}
문장::KECO.KECO미세먼지요약문장
{$$$세션.keco_target_name$$$ 대기질: $$$세션.keco_grade_text$$$ (통합지수 $$$세션.keco_khai_value$$$, 미세먼지 PM10 $$$세션.keco_pm10_value$$$㎍/㎥, PM2.5 $$$세션.keco_pm25_value$$$㎍/㎥) - $$$세션.keco_datatime$$$ 기준}
문장::KECO.KECO감시알림라인문장
{$$$세션.keco_target_name$$$ $$$세션.keco_grade_text$$$ (통합지수 $$$세션.keco_khai_value$$$)}
문장::TELEGRAM.미세먼지알림문장
{[미세먼지 알림] 통합대기환경지수 나쁨 이상인 관심지역이 있습니다.
$$$세션.keco_watch_alert_text$$$}
문장::TELEGRAM.KECO지역미지원문장
{"$$$세션.keco_target_name$$$"은(는) 아직 지원하지 않는 지역입니다. 지원 지역: 서울, 부산, 대구, 인천, 광주, 대전, 울산, 세종, 경기, 강원, 충북, 충남, 전북, 전남, 경북, 경남, 제주}
문장::TELEGRAM.KECO측정소없음문장
{$$$세션.keco_target_name$$$ 지역의 측정소 데이터를 찾을 수 없습니다.}
문장::TELEGRAM.KECO지역추가완료문장
{$$$세션.keco_target_name$$$을(를) 관심지역에 추가했습니다. 매일 $$$설정.KECO.alert_hour$$$시경 통합대기환경지수 나쁨 이상 시 알려드립니다.}
문장::TELEGRAM.KECO지역삭제완료문장
{$$$세션.keco_target_name$$$을(를) 관심지역에서 삭제했습니다.}
문장::TELEGRAM.KECO지역삭제실패문장
{$$$세션.keco_target_name$$$은(는) 등록된 관심지역이 아닙니다.}
문장::TELEGRAM.KECO지역조회빈목록문장
{등록된 관심지역이 없습니다. "미세먼지 지역 추가 서울"처럼 말씀해주세요.}
문장::TELEGRAM.KECO지역추가한도초과문장
{이미 관심지역이 5개 등록되어 있어 더 추가할 수 없습니다. 기존 지역을 삭제한 후 다시 시도해주세요.}
처리::TELEGRAM.텔레그램공휴일명령처리
{
  만약에(참)
    함수.단어분리(kmaspcd_cmd_word_list,세션.chat_input_text)
    함수.단어합치기(cmd_rest,세션.리스트.kmaspcd_cmd_word_list,1)
    처리.KMASPCD월파싱
}
처리::KMA_SPCD.KMASPCD월파싱
{
  만약에(세션.cmd_rest == NULL)
    함수.날짜(kmaspcd_query_year,%Y)
    함수.날짜(kmaspcd_query_month,%m)
    함수.저장(kmaspcd_mode,조회단건)
    전송.KMASPCD공휴일조회전송
  그외그외(세션.cmd_rest.길이 == 1)
    함수.날짜(kmaspcd_query_year,%Y)
    함수.저장(kmaspcd_query_month,0)
    함수.붙이기(kmaspcd_query_month,세션.cmd_rest)
    함수.저장(kmaspcd_mode,조회단건)
    전송.KMASPCD공휴일조회전송
  그외그외(세션.cmd_rest.길이 == 2)
    함수.날짜(kmaspcd_query_year,%Y)
    함수.저장(kmaspcd_query_month,세션.cmd_rest)
    함수.저장(kmaspcd_mode,조회단건)
    전송.KMASPCD공휴일조회전송
  그외그외(세션.cmd_rest.길이 == 4)
    함수.추출(kmaspcd_month_digit,세션.cmd_rest,0,1)
    함수.날짜(kmaspcd_query_year,%Y)
    함수.저장(kmaspcd_query_month,0)
    함수.붙이기(kmaspcd_query_month,세션.kmaspcd_month_digit)
    함수.저장(kmaspcd_mode,조회단건)
    전송.KMASPCD공휴일조회전송
  그외그외(세션.cmd_rest.길이 == 5)
    함수.추출(kmaspcd_query_month,세션.cmd_rest,0,2)
    함수.날짜(kmaspcd_query_year,%Y)
    함수.저장(kmaspcd_mode,조회단건)
    전송.KMASPCD공휴일조회전송
  그외
    함수.저장(kmaspcd_reply_text,문장.KMASPCD월인식실패문장)
    전송.KMASPCD응답전송
}
처리::FLOW.공휴일알림확인처리
{
  만약에(참)
    함수.날짜(kmaspcd_alert_hour_now,%H)
    타이머.공휴일알림타이머
    처리.공휴일알림시각비교
}
처리::FLOW.공휴일알림시각비교
{
  만약에(세션.kmaspcd_alert_hour_now == 설정.KMA_SPCD.alert_hour)
    함수.저장(kmaspcd_mode,폴링)
    처리.KMASPCD내일계산준비
  그외
    로그.출력(공휴일 알림 스킵 - 시각 불일치)
}
처리::KMA_SPCD.KMASPCD내일계산준비
{
  만약에(참)
    함수.날짜(kmaspcd_today_year,%Y)
    함수.날짜(kmaspcd_today_month,%m)
    함수.날짜(kmaspcd_today_day,%d)
    처리.KMASPCD말일판정
}
처리::KMA_SPCD.KMASPCD말일판정
{
  만약에(세션.kmaspcd_today_month == 01)
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 02)
    처리.KMASPCD윤년판정
  그외그외(세션.kmaspcd_today_month == 03)
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 04)
    함수.저장(kmaspcd_last_day,30)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 05)
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 06)
    함수.저장(kmaspcd_last_day,30)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 07)
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 08)
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 09)
    함수.저장(kmaspcd_last_day,30)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 10)
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_today_month == 11)
    함수.저장(kmaspcd_last_day,30)
    처리.KMASPCD내일계산
  그외
    함수.저장(kmaspcd_last_day,31)
    처리.KMASPCD내일계산
}
처리::KMA_SPCD.KMASPCD윤년판정
{
  만약에(참)
    함수.나머지(kmaspcd_mod4,세션.kmaspcd_today_year,4)
    함수.나머지(kmaspcd_mod100,세션.kmaspcd_today_year,100)
    함수.나머지(kmaspcd_mod400,세션.kmaspcd_today_year,400)
    처리.KMASPCD윤년분기
}
처리::KMA_SPCD.KMASPCD윤년분기
{
  만약에(세션.kmaspcd_mod4 == 0) 그리고(세션.kmaspcd_mod100 != 0)
    함수.저장(kmaspcd_last_day,29)
    처리.KMASPCD내일계산
  그외그외(세션.kmaspcd_mod400 == 0)
    함수.저장(kmaspcd_last_day,29)
    처리.KMASPCD내일계산
  그외
    함수.저장(kmaspcd_last_day,28)
    처리.KMASPCD내일계산
}
처리::KMA_SPCD.KMASPCD내일계산
{
  만약에(세션.kmaspcd_today_day == 세션.kmaspcd_last_day)
    처리.KMASPCD월경계처리
  그외
    함수.더하기(kmaspcd_tomorrow_day_raw,세션.kmaspcd_today_day,1)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,세션.kmaspcd_today_month)
    처리.KMASPCD일자패딩
}
처리::KMA_SPCD.KMASPCD월경계처리
{
  만약에(세션.kmaspcd_today_month == 12)
    함수.더하기(kmaspcd_tomorrow_year,세션.kmaspcd_today_year,1)
    함수.저장(kmaspcd_tomorrow_month,01)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 01)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,02)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 02)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,03)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 03)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,04)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 04)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,05)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 05)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,06)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 06)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,07)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 07)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,08)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 08)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,09)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 09)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,10)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외그외(세션.kmaspcd_today_month == 10)
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,11)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
  그외
    함수.저장(kmaspcd_tomorrow_year,세션.kmaspcd_today_year)
    함수.저장(kmaspcd_tomorrow_month,12)
    함수.저장(kmaspcd_tomorrow_day_raw,1)
    처리.KMASPCD일자패딩
}
처리::KMA_SPCD.KMASPCD일자패딩
{
  만약에(세션.kmaspcd_tomorrow_day_raw.길이 == 1)
    함수.저장(kmaspcd_tomorrow_day,0)
    함수.붙이기(kmaspcd_tomorrow_day,세션.kmaspcd_tomorrow_day_raw)
    처리.KMASPCD내일날짜조합
  그외
    함수.저장(kmaspcd_tomorrow_day,세션.kmaspcd_tomorrow_day_raw)
    처리.KMASPCD내일날짜조합
}
처리::KMA_SPCD.KMASPCD내일날짜조합
{
  만약에(참)
    함수.저장(kmaspcd_tomorrow_ymd,세션.kmaspcd_tomorrow_year)
    함수.붙이기(kmaspcd_tomorrow_ymd,세션.kmaspcd_tomorrow_month,세션.kmaspcd_tomorrow_day)
    함수.저장(kmaspcd_query_year,세션.kmaspcd_tomorrow_year)
    함수.저장(kmaspcd_query_month,세션.kmaspcd_tomorrow_month)
    전송.KMASPCD공휴일조회전송
}
처리::KMA_SPCD.KMASPCD응답분기처리
{
  만약에(수신메시지.response.body.totalCount == 0)
    함수.저장(kmaspcd_match_name,없음)
    처리.KMASPCD공휴일없음처리
  그외그외(수신메시지.response.body.totalCount == 1)
    함수.저장(kmaspcd_match_name,없음)
    함수.저장(kmaspcd_single_locdate,수신메시지.response.body.items.item.locdate)
    함수.저장(kmaspcd_single_name,수신메시지.response.body.items.item.dateName)
    처리.KMASPCD단일항목처리
  그외
    함수.객체저장(kmaspcd_items,수신메시지.response.body.items.item)
    함수.저장(kmaspcd_idx,0)
    함수.저장(kmaspcd_summary_lines,없음)
    함수.저장(kmaspcd_summary_found,0)
    함수.저장(kmaspcd_match_name,없음)
    처리.KMASPCD항목순회
}
처리::KMA_SPCD.KMASPCD공휴일없음처리
{
  만약에(세션.kmaspcd_mode == 조회단건)
    함수.저장(kmaspcd_reply_text,문장.KMASPCD월공휴일없음문장)
    전송.KMASPCD응답전송
  그외
    로그.출력(공휴일 알림 - 내일 공휴일 아님, 해당 월 공휴일 없음)
}
처리::KMA_SPCD.KMASPCD단일항목처리
{
  만약에(세션.kmaspcd_mode == 조회단건)
    함수.추출(kmaspcd_single_day,세션.kmaspcd_single_locdate,6,2)
    함수.저장(kmaspcd_reply_text,문장.KMASPCD월공휴일단일문장)
    전송.KMASPCD응답전송
  그외그외(세션.kmaspcd_single_locdate == 세션.kmaspcd_tomorrow_ymd)
    함수.저장(kmaspcd_match_name,세션.kmaspcd_single_name)
    전송.KMASPCD알림전송
  그외
    로그.출력(공휴일 알림 - 내일 공휴일 아님)
}
처리::KMA_SPCD.KMASPCD항목순회
{
  만약에(세션.객체.kmaspcd_items[세션.kmaspcd_idx].locdate == NULL)
    처리.KMASPCD순회완료
  그외
    처리.KMASPCD항목검사
}
처리::KMA_SPCD.KMASPCD항목검사
{
  만약에(세션.kmaspcd_mode == 조회단건)
    함수.저장(kmaspcd_day_raw,세션.객체.kmaspcd_items[세션.kmaspcd_idx].locdate)
    함수.추출(kmaspcd_day2,세션.kmaspcd_day_raw,6,2)
    처리.KMASPCD요약항목추가
  그외그외(세션.객체.kmaspcd_items[세션.kmaspcd_idx].locdate == 세션.kmaspcd_tomorrow_ymd)
    함수.저장(kmaspcd_match_name,세션.객체.kmaspcd_items[세션.kmaspcd_idx].dateName)
    함수.더하기(kmaspcd_idx,세션.kmaspcd_idx,1)
    처리.KMASPCD항목순회
  그외
    함수.더하기(kmaspcd_idx,세션.kmaspcd_idx,1)
    처리.KMASPCD항목순회
}
처리::KMA_SPCD.KMASPCD요약항목추가
{
  만약에(세션.kmaspcd_summary_found == 0)
    함수.저장(kmaspcd_summary_lines,문장.KMASPCD요약항목문장)
    함수.저장(kmaspcd_summary_found,1)
    함수.더하기(kmaspcd_idx,세션.kmaspcd_idx,1)
    처리.KMASPCD항목순회
  그외
    함수.붙이기(kmaspcd_summary_lines,|,문장.KMASPCD요약항목문장)
    함수.더하기(kmaspcd_idx,세션.kmaspcd_idx,1)
    처리.KMASPCD항목순회
}
처리::KMA_SPCD.KMASPCD순회완료
{
  만약에(세션.kmaspcd_mode == 조회단건) 그리고(세션.kmaspcd_summary_found == 1)
    함수.저장(kmaspcd_reply_text,문장.KMASPCD월공휴일요약문장)
    전송.KMASPCD응답전송
  그외그외(세션.kmaspcd_mode == 조회단건)
    함수.저장(kmaspcd_reply_text,문장.KMASPCD월공휴일없음문장)
    전송.KMASPCD응답전송
  그외그외(세션.kmaspcd_match_name != 없음)
    전송.KMASPCD알림전송
  그외
    로그.출력(공휴일 알림 - 내일 공휴일 아님)
}
전송::KMA_SPCD.KMASPCD공휴일조회전송
{
  전송메시지.메소드 = GET
  전송메시지.주소.도메인 = 설정.KMA_SPCD.domain
  전송메시지.주소.경로 = /getHoliDeInfo
  전송메시지.주소.파라미터[0].key = ServiceKey
  전송메시지.주소.파라미터[0].val = 설정.K_DATA.service_key
  전송메시지.주소.파라미터[1].key = pageNo
  전송메시지.주소.파라미터[1].val = 1
  전송메시지.주소.파라미터[2].key = numOfRows
  전송메시지.주소.파라미터[2].val = 31
  전송메시지.주소.파라미터[3].key = solYear
  전송메시지.주소.파라미터[3].val = 세션.kmaspcd_query_year
  전송메시지.주소.파라미터[4].key = solMonth
  전송메시지.주소.파라미터[4].val = 세션.kmaspcd_query_month
  전송메시지.주소.파라미터[5].key = _type
  전송메시지.주소.파라미터[5].val = json
}
전송::FLOW.KMASPCD응답전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 세션.kmaspcd_reply_text
}
전송::FLOW.KMASPCD알림전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 문장.KMASPCD알림문장
}
타이머::KMA_SPCD.공휴일알림타이머
{
  전송메시지.이벤트명 = 공휴일알림틱
  전송메시지.시간 = 3600000
}
문장::KMA_SPCD.KMASPCD요약항목문장
{$$$세션.객체.kmaspcd_items[세션.kmaspcd_idx].dateName$$$($$$세션.kmaspcd_query_month$$$/$$$세션.kmaspcd_day2$$$)}
문장::KMA_SPCD.KMASPCD월공휴일요약문장
{$$$세션.kmaspcd_query_month$$$월 공휴일: $$$세션.kmaspcd_summary_lines$$$}
문장::KMA_SPCD.KMASPCD월공휴일단일문장
{$$$세션.kmaspcd_query_month$$$월 공휴일: $$$세션.kmaspcd_single_name$$$($$$세션.kmaspcd_query_month$$$/$$$세션.kmaspcd_single_day$$$)}
문장::KMA_SPCD.KMASPCD월공휴일없음문장
{$$$세션.kmaspcd_query_month$$$월에는 공휴일이 없습니다.}
문장::TELEGRAM.KMASPCD알림문장
{[공휴일 알림] 내일($$$세션.kmaspcd_tomorrow_month$$$/$$$세션.kmaspcd_tomorrow_day$$$)은(는) "$$$세션.kmaspcd_match_name$$$"입니다. 쉬는 날이에요!}
문장::TELEGRAM.KMASPCD월인식실패문장
{월 형식을 이해하지 못했습니다. "공휴일" 또는 "공휴일 10월"처럼 말씀해주세요.}
처리::FLOW.MOLIT관심지역초기화
{
  만약에(참)
    함수.저장(molit_watch_csv,없음)
    함수.저장(molit_seed_found,0)
    함수.저장(molit_ini_category,MOLIT_WATCHLIST)
    함수.저장(molit_ini_clear_value,없음)
    함수.저장(molit_ini_pipe,|)
    함수.저장(molit_ini_colon,:)
    처리.MOLIT시드확인1
}
처리::FLOW.MOLIT시드확인1
{
  만약에(설정.MOLIT_WATCHLIST.지역1 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역1 != 없음) 그리고(세션.molit_seed_found != 1)
    함수.저장(molit_watch_csv,설정.MOLIT_WATCHLIST.지역1)
    함수.저장(molit_seed_found,1)
    처리.MOLIT시드확인2
  그외그외(설정.MOLIT_WATCHLIST.지역1 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역1 != 없음)
    함수.붙이기(molit_watch_csv,|,설정.MOLIT_WATCHLIST.지역1)
    처리.MOLIT시드확인2
  그외
    처리.MOLIT시드확인2
}
처리::FLOW.MOLIT시드확인2
{
  만약에(설정.MOLIT_WATCHLIST.지역2 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역2 != 없음) 그리고(세션.molit_seed_found != 1)
    함수.저장(molit_watch_csv,설정.MOLIT_WATCHLIST.지역2)
    함수.저장(molit_seed_found,1)
    처리.MOLIT시드확인3
  그외그외(설정.MOLIT_WATCHLIST.지역2 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역2 != 없음)
    함수.붙이기(molit_watch_csv,|,설정.MOLIT_WATCHLIST.지역2)
    처리.MOLIT시드확인3
  그외
    처리.MOLIT시드확인3
}
처리::FLOW.MOLIT시드확인3
{
  만약에(설정.MOLIT_WATCHLIST.지역3 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역3 != 없음) 그리고(세션.molit_seed_found != 1)
    함수.저장(molit_watch_csv,설정.MOLIT_WATCHLIST.지역3)
    함수.저장(molit_seed_found,1)
    처리.MOLIT시드확인4
  그외그외(설정.MOLIT_WATCHLIST.지역3 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역3 != 없음)
    함수.붙이기(molit_watch_csv,|,설정.MOLIT_WATCHLIST.지역3)
    처리.MOLIT시드확인4
  그외
    처리.MOLIT시드확인4
}
처리::FLOW.MOLIT시드확인4
{
  만약에(설정.MOLIT_WATCHLIST.지역4 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역4 != 없음) 그리고(세션.molit_seed_found != 1)
    함수.저장(molit_watch_csv,설정.MOLIT_WATCHLIST.지역4)
    함수.저장(molit_seed_found,1)
    처리.MOLIT시드확인5
  그외그외(설정.MOLIT_WATCHLIST.지역4 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역4 != 없음)
    함수.붙이기(molit_watch_csv,|,설정.MOLIT_WATCHLIST.지역4)
    처리.MOLIT시드확인5
  그외
    처리.MOLIT시드확인5
}
처리::FLOW.MOLIT시드확인5
{
  만약에(설정.MOLIT_WATCHLIST.지역5 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역5 != 없음) 그리고(세션.molit_seed_found != 1)
    함수.저장(molit_watch_csv,설정.MOLIT_WATCHLIST.지역5)
    함수.저장(molit_seed_found,1)
    로그.출력(실거래가 관심지역 시드 로딩 완료)
  그외그외(설정.MOLIT_WATCHLIST.지역5 != NULL) 그리고(설정.MOLIT_WATCHLIST.지역5 != 없음)
    함수.붙이기(molit_watch_csv,|,설정.MOLIT_WATCHLIST.지역5)
    로그.출력(실거래가 관심지역 시드 로딩 완료)
  그외
    로그.출력(실거래가 관심지역 시드 로딩 완료)
}
처리::TELEGRAM.텔레그램실거래가명령처리
{
  만약에(참)
    함수.단어분리(molit_cmd_word_list,세션.chat_input_text)
    함수.단어합치기(cmd_rest,세션.리스트.molit_cmd_word_list,1)
    처리.MOLIT명령분기
}
처리::TELEGRAM.MOLIT명령분기
{
  만약에(세션.cmd_rest === 지역추가)
    처리.텔레그램MOLIT지역추가명령처리
  그외그외(세션.cmd_rest === 지역삭제)
    처리.텔레그램MOLIT지역삭제명령처리
  그외그외(세션.cmd_rest === 지역조회)
    처리.텔레그램MOLIT지역조회명령처리
  그외
    처리.MOLIT명령파싱
}
처리::TELEGRAM.텔레그램MOLIT지역추가명령처리
{
  만약에(참)
    함수.단어분리(molit_add_word_list,세션.cmd_rest)
    함수.단어합치기(molit_target_name,세션.리스트.molit_add_word_list,1)
    함수.저장(molit_lookup_name,세션.molit_target_name)
    함수.저장(molit_lookup_mode,추가)
    처리.MOLIT지역코드조회
}
처리::TELEGRAM.텔레그램MOLIT지역삭제명령처리
{
  만약에(참)
    함수.단어분리(molit_del_word_list,세션.cmd_rest)
    함수.단어합치기(molit_target_name,세션.리스트.molit_del_word_list,1)
    함수.저장(molit_del_match_prefix,세션.molit_target_name)
    함수.붙이기(molit_del_match_prefix,:)
    함수.쪼개기(molit_watch_list,세션.molit_watch_csv,|)
    함수.저장(molit_del_idx,0)
    함수.저장(molit_del_found,0)
    함수.저장(molit_new_csv,없음)
    함수.저장(molit_new_found,0)
    처리.MOLIT지역삭제순회
}
처리::MOLIT.MOLIT지역삭제순회
{
  만약에(세션.molit_del_idx >= 세션.리스트.molit_watch_list.SIZE)
    처리.MOLIT지역삭제완료
  그외
    처리.MOLIT지역삭제항목검사
}
처리::MOLIT.MOLIT지역삭제항목검사
{
  만약에(세션.리스트.molit_watch_list[세션.molit_del_idx] === 세션.molit_del_match_prefix)
    함수.더하기(molit_del_found,세션.molit_del_found,1)
    함수.더하기(molit_del_idx,세션.molit_del_idx,1)
    처리.MOLIT지역삭제순회
  그외
    처리.MOLIT지역삭제보존
}
처리::MOLIT.MOLIT지역삭제보존
{
  만약에(세션.molit_new_found == 0)
    함수.저장(molit_new_csv,세션.리스트.molit_watch_list[세션.molit_del_idx])
    함수.저장(molit_new_found,1)
    함수.더하기(molit_del_idx,세션.molit_del_idx,1)
    처리.MOLIT지역삭제순회
  그외
    함수.붙이기(molit_new_csv,세션.molit_ini_pipe,세션.리스트.molit_watch_list[세션.molit_del_idx])
    함수.더하기(molit_del_idx,세션.molit_del_idx,1)
    처리.MOLIT지역삭제순회
}
처리::MOLIT.MOLIT지역삭제완료
{
  만약에(세션.molit_del_found > 0)
    함수.저장(molit_watch_csv,세션.molit_new_csv)
    함수.저장(molit_ini_del_found,0)
    처리.MOLIT지역ini삭제찾기1
  그외
    함수.저장(molit_reply_text,문장.MOLIT관심지역삭제실패문장)
    전송.MOLIT응답전송
}
처리::MOLIT.MOLIT지역ini삭제찾기1
{
  만약에(설정.MOLIT_WATCHLIST.지역1 === 세션.molit_del_match_prefix) 그리고(세션.molit_ini_del_found != 1)
    함수.저장(molit_ini_slot_key,지역1)
    함수.설정저장(세션.molit_ini_category,세션.molit_ini_slot_key,세션.molit_ini_clear_value)
    함수.저장(molit_ini_del_found,1)
    처리.MOLIT지역ini삭제찾기2
  그외
    처리.MOLIT지역ini삭제찾기2
}
처리::MOLIT.MOLIT지역ini삭제찾기2
{
  만약에(설정.MOLIT_WATCHLIST.지역2 === 세션.molit_del_match_prefix) 그리고(세션.molit_ini_del_found != 1)
    함수.저장(molit_ini_slot_key,지역2)
    함수.설정저장(세션.molit_ini_category,세션.molit_ini_slot_key,세션.molit_ini_clear_value)
    함수.저장(molit_ini_del_found,1)
    처리.MOLIT지역ini삭제찾기3
  그외
    처리.MOLIT지역ini삭제찾기3
}
처리::MOLIT.MOLIT지역ini삭제찾기3
{
  만약에(설정.MOLIT_WATCHLIST.지역3 === 세션.molit_del_match_prefix) 그리고(세션.molit_ini_del_found != 1)
    함수.저장(molit_ini_slot_key,지역3)
    함수.설정저장(세션.molit_ini_category,세션.molit_ini_slot_key,세션.molit_ini_clear_value)
    함수.저장(molit_ini_del_found,1)
    처리.MOLIT지역ini삭제찾기4
  그외
    처리.MOLIT지역ini삭제찾기4
}
처리::MOLIT.MOLIT지역ini삭제찾기4
{
  만약에(설정.MOLIT_WATCHLIST.지역4 === 세션.molit_del_match_prefix) 그리고(세션.molit_ini_del_found != 1)
    함수.저장(molit_ini_slot_key,지역4)
    함수.설정저장(세션.molit_ini_category,세션.molit_ini_slot_key,세션.molit_ini_clear_value)
    함수.저장(molit_ini_del_found,1)
    처리.MOLIT지역ini삭제찾기5
  그외
    처리.MOLIT지역ini삭제찾기5
}
처리::MOLIT.MOLIT지역ini삭제찾기5
{
  만약에(설정.MOLIT_WATCHLIST.지역5 === 세션.molit_del_match_prefix) 그리고(세션.molit_ini_del_found != 1)
    함수.저장(molit_ini_slot_key,지역5)
    함수.설정저장(세션.molit_ini_category,세션.molit_ini_slot_key,세션.molit_ini_clear_value)
    처리.MOLIT지역삭제응답
  그외
    처리.MOLIT지역삭제응답
}
처리::MOLIT.MOLIT지역삭제응답
{
  만약에(참)
    함수.저장(molit_reply_text,문장.MOLIT관심지역삭제완료문장)
    전송.MOLIT응답전송
}
처리::TELEGRAM.텔레그램MOLIT지역조회명령처리
{
  만약에(세션.molit_watch_csv == 없음)
    함수.저장(molit_reply_text,문장.MOLIT관심지역빈목록문장)
    전송.MOLIT응답전송
  그외
    함수.쪼개기(molit_list_watch,세션.molit_watch_csv,|)
    함수.저장(molit_list_idx,0)
    함수.저장(molit_list_lines,없음)
    처리.MOLIT지역목록순회
}
처리::MOLIT.MOLIT지역목록순회
{
  만약에(세션.molit_list_idx >= 세션.리스트.molit_list_watch.SIZE)
    함수.저장(molit_reply_text,문장.MOLIT관심지역목록문장)
    전송.MOLIT응답전송
  그외
    함수.쪼개기(molit_list_parts,세션.리스트.molit_list_watch[세션.molit_list_idx],:)
    처리.MOLIT지역목록라인추가
}
처리::MOLIT.MOLIT지역목록라인추가
{
  만약에(세션.molit_list_idx == 0)
    함수.저장(molit_list_lines,문장.MOLIT지역목록라인문장)
    함수.더하기(molit_list_idx,세션.molit_list_idx,1)
    처리.MOLIT지역목록순회
  그외
    함수.붙이기(molit_list_lines,|,문장.MOLIT지역목록라인문장)
    함수.더하기(molit_list_idx,세션.molit_list_idx,1)
    처리.MOLIT지역목록순회
}
처리::MOLIT.MOLIT지역코드조회
{
  만약에(세션.molit_lookup_name == 종로구)
    함수.저장(molit_lookup_code,11110)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 중구)
    함수.저장(molit_lookup_code,11140)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 용산구)
    함수.저장(molit_lookup_code,11170)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 성동구)
    함수.저장(molit_lookup_code,11200)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 광진구)
    함수.저장(molit_lookup_code,11215)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 동대문구)
    함수.저장(molit_lookup_code,11230)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 중랑구)
    함수.저장(molit_lookup_code,11260)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 성북구)
    함수.저장(molit_lookup_code,11290)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 강북구)
    함수.저장(molit_lookup_code,11305)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 도봉구)
    함수.저장(molit_lookup_code,11320)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 노원구)
    함수.저장(molit_lookup_code,11350)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 은평구)
    함수.저장(molit_lookup_code,11380)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 서대문구)
    함수.저장(molit_lookup_code,11410)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 마포구)
    함수.저장(molit_lookup_code,11440)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 양천구)
    함수.저장(molit_lookup_code,11470)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 강서구)
    함수.저장(molit_lookup_code,11500)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 구로구)
    함수.저장(molit_lookup_code,11530)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 금천구)
    함수.저장(molit_lookup_code,11545)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 영등포구)
    함수.저장(molit_lookup_code,11560)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 동작구)
    함수.저장(molit_lookup_code,11590)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 관악구)
    함수.저장(molit_lookup_code,11620)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 서초구)
    함수.저장(molit_lookup_code,11650)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 강남구)
    함수.저장(molit_lookup_code,11680)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 송파구)
    함수.저장(molit_lookup_code,11710)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외그외(세션.molit_lookup_name == 강동구)
    함수.저장(molit_lookup_code,11740)
    함수.저장(molit_lookup_found,1)
    처리.MOLIT지역코드조회완료
  그외
    함수.저장(molit_lookup_found,0)
    처리.MOLIT지역코드조회완료
}
처리::MOLIT.MOLIT지역코드조회완료
{
  만약에(세션.molit_lookup_mode == 조회)
    처리.MOLIT지역검증완료
  그외
    처리.MOLIT관심지역추가확정
}
처리::MOLIT.MOLIT지역검증완료
{
  만약에(세션.molit_lookup_found == 0)
    함수.저장(molit_reply_text,문장.MOLIT지역미지원문장)
    전송.MOLIT응답전송
  그외
    함수.저장(molit_region_name,세션.molit_lookup_name)
    함수.저장(molit_region_code,세션.molit_lookup_code)
    전송.MOLIT실거래가조회전송
}
처리::MOLIT.MOLIT관심지역추가확정
{
  만약에(세션.molit_lookup_found == 0)
    함수.저장(molit_reply_text,문장.MOLIT지역미지원문장)
    전송.MOLIT응답전송
  그외
    함수.저장(molit_resolved_code,세션.molit_lookup_code)
    함수.저장(molit_ini_write_value,세션.molit_target_name)
    함수.붙이기(molit_ini_write_value,세션.molit_ini_colon,세션.molit_resolved_code)
    함수.저장(molit_ini_found,0)
    처리.MOLIT관심지역ini빈슬롯찾기1
}
처리::MOLIT.MOLIT관심지역ini빈슬롯찾기1
{
  만약에(설정.MOLIT_WATCHLIST.지역1 == NULL)
    함수.저장(molit_ini_slot_key,지역1)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외그외(설정.MOLIT_WATCHLIST.지역1 == 없음)
    함수.저장(molit_ini_slot_key,지역1)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외
    처리.MOLIT관심지역ini빈슬롯찾기2
}
처리::MOLIT.MOLIT관심지역ini빈슬롯찾기2
{
  만약에(설정.MOLIT_WATCHLIST.지역2 == NULL)
    함수.저장(molit_ini_slot_key,지역2)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외그외(설정.MOLIT_WATCHLIST.지역2 == 없음)
    함수.저장(molit_ini_slot_key,지역2)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외
    처리.MOLIT관심지역ini빈슬롯찾기3
}
처리::MOLIT.MOLIT관심지역ini빈슬롯찾기3
{
  만약에(설정.MOLIT_WATCHLIST.지역3 == NULL)
    함수.저장(molit_ini_slot_key,지역3)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외그외(설정.MOLIT_WATCHLIST.지역3 == 없음)
    함수.저장(molit_ini_slot_key,지역3)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외
    처리.MOLIT관심지역ini빈슬롯찾기4
}
처리::MOLIT.MOLIT관심지역ini빈슬롯찾기4
{
  만약에(설정.MOLIT_WATCHLIST.지역4 == NULL)
    함수.저장(molit_ini_slot_key,지역4)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외그외(설정.MOLIT_WATCHLIST.지역4 == 없음)
    함수.저장(molit_ini_slot_key,지역4)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외
    처리.MOLIT관심지역ini빈슬롯찾기5
}
처리::MOLIT.MOLIT관심지역ini빈슬롯찾기5
{
  만약에(설정.MOLIT_WATCHLIST.지역5 == NULL)
    함수.저장(molit_ini_slot_key,지역5)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외그외(설정.MOLIT_WATCHLIST.지역5 == 없음)
    함수.저장(molit_ini_slot_key,지역5)
    함수.저장(molit_ini_found,1)
    처리.MOLIT관심지역ini쓰기
  그외
    처리.MOLIT관심지역ini쓰기
}
처리::MOLIT.MOLIT관심지역ini쓰기
{
  만약에(세션.molit_ini_found == 1)
    함수.설정저장(세션.molit_ini_category,세션.molit_ini_slot_key,세션.molit_ini_write_value)
    처리.MOLIT관심지역추가세션갱신
  그외
    함수.저장(molit_reply_text,문장.MOLIT관심지역추가한도초과문장)
    전송.MOLIT응답전송
}
처리::MOLIT.MOLIT관심지역추가세션갱신
{
  만약에(세션.molit_watch_csv == 없음)
    함수.저장(molit_watch_csv,세션.molit_ini_write_value)
    함수.저장(molit_reply_text,문장.MOLIT관심지역추가완료문장)
    전송.MOLIT응답전송
  그외
    함수.붙이기(molit_watch_csv,세션.molit_ini_pipe,세션.molit_ini_write_value)
    함수.저장(molit_reply_text,문장.MOLIT관심지역추가완료문장)
    전송.MOLIT응답전송
}
처리::MOLIT.MOLIT명령파싱
{
  만약에(세션.cmd_rest == NULL)
    함수.날짜(molit_deal_ymd,%Y%m)
    함수.저장(molit_region_requested,0)
    처리.MOLIT월분해
  그외
    함수.단어분리(molit_arg_word_list,세션.cmd_rest)
    처리.MOLIT인자개수분기
}
처리::MOLIT.MOLIT인자개수분기
{
  만약에(세션.리스트.molit_arg_word_list.SIZE == 1)
    함수.저장(molit_month_candidate,세션.리스트.molit_arg_word_list[0])
    함수.저장(molit_region_requested,0)
    처리.MOLIT단어유효성검사
  그외그외(세션.리스트.molit_arg_word_list.SIZE == 2)
    함수.저장(molit_region_candidate,세션.리스트.molit_arg_word_list[0])
    함수.저장(molit_month_candidate,세션.리스트.molit_arg_word_list[1])
    함수.저장(molit_region_requested,1)
    처리.MOLIT단어유효성검사
  그외
    함수.저장(molit_reply_text,문장.MOLIT파싱실패문장)
    전송.MOLIT응답전송
}
처리::MOLIT.MOLIT단어유효성검사
{
  만약에(세션.molit_month_candidate.길이 == 6)
    함수.저장(molit_deal_ymd,세션.molit_month_candidate)
    처리.MOLIT월분해
  그외
    함수.저장(molit_reply_text,문장.MOLIT파싱실패문장)
    전송.MOLIT응답전송
}
처리::MOLIT.MOLIT월분해
{
  만약에(세션.molit_region_requested == 1)
    함수.추출(molit_year,세션.molit_deal_ymd,0,4)
    함수.추출(molit_month,세션.molit_deal_ymd,4,2)
    함수.저장(molit_lookup_name,세션.molit_region_candidate)
    함수.저장(molit_lookup_mode,조회)
    처리.MOLIT지역코드조회
  그외
    함수.추출(molit_year,세션.molit_deal_ymd,0,4)
    함수.추출(molit_month,세션.molit_deal_ymd,4,2)
    함수.저장(molit_region_name,설정.MOLIT.default_region_name)
    함수.저장(molit_region_code,설정.MOLIT.default_region_code)
    전송.MOLIT실거래가조회전송
}
처리::MOLIT.MOLIT응답분기처리
{
  만약에(수신메시지.response.body.totalCount == 0)
    함수.저장(molit_reply_text,문장.MOLIT거래없음문장)
    전송.MOLIT응답전송
  그외그외(수신메시지.response.body.totalCount == 1)
    함수.저장(molit_apt_nm,수신메시지.response.body.items.item.aptNm)
    함수.저장(molit_area,수신메시지.response.body.items.item.excluUseAr)
    함수.저장(molit_amount,수신메시지.response.body.items.item.dealAmount)
    함수.저장(molit_day,수신메시지.response.body.items.item.dealDay)
    함수.저장(molit_summary_lines,문장.MOLIT거래라인문장_단일)
    함수.저장(molit_reply_text,문장.MOLIT요약문장_단일)
    전송.MOLIT응답전송
  그외
    함수.객체저장(molit_items,수신메시지.response.body.items.item)
    함수.저장(molit_idx,0)
    함수.저장(molit_count,0)
    함수.저장(molit_summary_lines,없음)
    처리.MOLIT항목순회
}
처리::MOLIT.MOLIT항목순회
{
  만약에(세션.객체.molit_items[세션.molit_idx].aptNm == NULL)
    함수.저장(molit_reply_text,문장.MOLIT요약문장)
    전송.MOLIT응답전송
  그외그외(세션.molit_count >= 5)
    함수.저장(molit_reply_text,문장.MOLIT요약문장)
    전송.MOLIT응답전송
  그외
    처리.MOLIT항목추가
}
처리::MOLIT.MOLIT항목추가
{
  만약에(세션.molit_count == 0)
    함수.저장(molit_summary_lines,문장.MOLIT거래라인문장)
    함수.더하기(molit_count,세션.molit_count,1)
    함수.더하기(molit_idx,세션.molit_idx,1)
    처리.MOLIT항목순회
  그외
    함수.붙이기(molit_summary_lines,|,문장.MOLIT거래라인문장)
    함수.더하기(molit_count,세션.molit_count,1)
    함수.더하기(molit_idx,세션.molit_idx,1)
    처리.MOLIT항목순회
}
전송::MOLIT.MOLIT실거래가조회전송
{
  전송메시지.메소드 = GET
  전송메시지.주소.도메인 = 설정.MOLIT.domain
  전송메시지.주소.경로 = /getRTMSDataSvcAptTrade
  전송메시지.주소.파라미터[0].key = serviceKey
  전송메시지.주소.파라미터[0].val = 설정.K_DATA.service_key
  전송메시지.주소.파라미터[1].key = LAWD_CD
  전송메시지.주소.파라미터[1].val = 세션.molit_region_code
  전송메시지.주소.파라미터[2].key = DEAL_YMD
  전송메시지.주소.파라미터[2].val = 세션.molit_deal_ymd
  전송메시지.주소.파라미터[3].key = pageNo
  전송메시지.주소.파라미터[3].val = 1
  전송메시지.주소.파라미터[4].key = numOfRows
  전송메시지.주소.파라미터[4].val = 50
  전송메시지.주소.파라미터[5].key = _type
  전송메시지.주소.파라미터[5].val = json
}
전송::FLOW.MOLIT응답전송
{
  전송메시지.이벤트명 = 봇응답
  전송메시지.text = 세션.molit_reply_text
}
문장::MOLIT.MOLIT거래라인문장
{$$$세션.객체.molit_items[세션.molit_idx].aptNm$$$ $$$세션.객체.molit_items[세션.molit_idx].excluUseAr$$$㎡ $$$세션.객체.molit_items[세션.molit_idx].dealAmount$$$만원 ($$$세션.객체.molit_items[세션.molit_idx].dealDay$$$일)}
문장::MOLIT.MOLIT거래라인문장_단일
{$$$세션.molit_apt_nm$$$ $$$세션.molit_area$$$㎡ $$$세션.molit_amount$$$만원 ($$$세션.molit_day$$$일)}
문장::MOLIT.MOLIT요약문장
{$$$세션.molit_region_name$$$ $$$세션.molit_year$$$년 $$$세션.molit_month$$$월 아파트 매매 실거래가 (최근 $$$세션.molit_count$$$건): $$$세션.molit_summary_lines$$$}
문장::MOLIT.MOLIT요약문장_단일
{$$$세션.molit_region_name$$$ $$$세션.molit_year$$$년 $$$세션.molit_month$$$월 아파트 매매 실거래가 (1건): $$$세션.molit_summary_lines$$$}
문장::MOLIT.MOLIT거래없음문장
{$$$세션.molit_region_name$$$ $$$세션.molit_year$$$년 $$$세션.molit_month$$$월에는 아파트 매매 거래 내역이 없습니다.}
문장::TELEGRAM.MOLIT파싱실패문장
{계약년월 형식을 이해하지 못했습니다. "실거래가" 또는 "실거래가 202410"처럼 말씀해주세요.}
문장::TELEGRAM.MOLIT지역미지원문장
{$$$세션.molit_lookup_name$$$은(는) 아직 지원하지 않는 지역입니다.}
문장::TELEGRAM.MOLIT관심지역추가완료문장
{$$$세션.molit_target_name$$$($$$세션.molit_resolved_code$$$)를 관심지역에 추가했습니다.}
문장::TELEGRAM.MOLIT관심지역추가한도초과문장
{이미 관심지역이 5개 등록되어 있어 더 추가할 수 없습니다. 기존 지역을 삭제한 후 다시 시도해주세요.}
문장::TELEGRAM.MOLIT관심지역삭제완료문장
{$$$세션.molit_target_name$$$을(를) 관심지역에서 삭제했습니다.}
문장::TELEGRAM.MOLIT관심지역삭제실패문장
{$$$세션.molit_target_name$$$은(는) 등록된 관심지역이 아닙니다.}
문장::TELEGRAM.MOLIT관심지역빈목록문장
{등록된 관심지역이 없습니다. "실거래가 지역추가 강남구"처럼 말씀해주세요.}
문장::TELEGRAM.MOLIT관심지역목록문장
{등록된 관심지역: $$$세션.molit_list_lines$$$}
문장::MOLIT.MOLIT지역목록라인문장
{$$$세션.리스트.molit_list_parts[0]$$$($$$세션.리스트.molit_list_parts[1]$$$)}
